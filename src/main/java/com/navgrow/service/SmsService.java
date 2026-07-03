/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * SmsService — provider-agnostic SMS delivery.
 *
 * Controlled by config (sms.provider):
 *   - "log"    (default) → writes the message to the application log. Safe for
 *                          development and staging without a paid SMS account.
 *   - "msg91"            → sends via MSG91 (popular Indian transactional SMS gateway).
 *   - "twilio"           → sends via Twilio.
 *
 * All sends are best-effort: failures are logged but never thrown, so SMS issues
 * cannot break a checkout, login, or any other user flow.
 *
 * Provider credentials and DLT template ids are supplied via environment variables
 * (see application.yml). No secrets are hard-coded.
 */
@Service
@Slf4j
public class SmsService {

    private final RestTemplate restTemplate;

    @Value("${sms.provider:log}")            private String provider;
    @Value("${sms.sender-id:NAVGRW}")        private String senderId;
    // MSG91
    @Value("${sms.msg91.auth-key:}")         private String msg91AuthKey;
    @Value("${sms.msg91.otp-template-id:}")  private String msg91OtpTemplate;
    // Twilio
    @Value("${sms.twilio.account-sid:}")     private String twilioSid;
    @Value("${sms.twilio.auth-token:}")      private String twilioToken;
    @Value("${sms.twilio.from-number:}")     private String twilioFrom;

    public SmsService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** Send a plain transactional SMS. Returns true if the send was attempted successfully. */
    public boolean send(String phone, String message) {
        String to = normalise(phone);
        if (to == null) {
            log.warn("[SMS] Skipped — invalid phone '{}'", phone);
            return false;
        }
        try {
            switch (provider == null ? "log" : provider.toLowerCase()) {
                case "msg91":  return sendViaMsg91(to, message, null);
                case "twilio": return sendViaTwilio(to, message);
                default:       return sendViaLog(to, message);
            }
        } catch (Exception e) {
            log.error("[SMS] Delivery failed for {}: {}", to, e.getMessage());
            return false;
        }
    }

    /** Send an OTP. With MSG91 this uses the OTP template; otherwise a plain message. */
    public boolean sendOtp(String phone, String code) {
        String to = normalise(phone);
        if (to == null) return false;
        String message = "Your Navgrow verification code is " + code + ". Valid for 5 minutes. Do not share it with anyone.";
        try {
            if ("msg91".equalsIgnoreCase(provider)) {
                return sendViaMsg91(to, message, code);
            } else if ("twilio".equalsIgnoreCase(provider)) {
                return sendViaTwilio(to, message);
            }
            return sendViaLog(to, message);
        } catch (Exception e) {
            log.error("[SMS] OTP delivery failed for {}: {}", to, e.getMessage());
            return false;
        }
    }

    // ── Providers ─────────────────────────────────────────────────────────────

    private boolean sendViaLog(String to, String message) {
        log.info("[SMS:log] To {} (sender {}): {}", to, senderId, message);
        log.info("[SMS:log] Configure 'sms.provider' (msg91/twilio) for real delivery.");
        return true;
    }

    /** MSG91 flow API. Requires sms.msg91.auth-key and (for OTP) a DLT template id. */
    private boolean sendViaMsg91(String to, String message, String otpCode) {
        if (msg91AuthKey == null || msg91AuthKey.isBlank()) {
            log.warn("[SMS:msg91] Missing auth key — falling back to log.");
            return sendViaLog(to, message);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", msg91AuthKey);

        // MSG91 expects national number without the +; assume India (+91) numbers.
        String recipient = to.startsWith("+") ? to.substring(1) : to;

        Map<String, Object> body = new HashMap<>();
        if (otpCode != null && msg91OtpTemplate != null && !msg91OtpTemplate.isBlank()) {
            // OTP template flow
            body.put("template_id", msg91OtpTemplate);
            body.put("mobile", recipient);
            Map<String, String> vars = new HashMap<>();
            vars.put("otp", otpCode);
            body.put("OTP", otpCode);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://control.msg91.com/api/v5/otp", req, String.class);
        } else {
            // Plain flow message
            body.put("sender", senderId);
            body.put("route", "4");
            body.put("country", "91");
            Map<String, Object> sms = new HashMap<>();
            sms.put("message", message);
            sms.put("to", new String[]{recipient});
            body.put("sms", new Object[]{sms});
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            restTemplate.postForEntity("https://control.msg91.com/api/v5/flow/", req, String.class);
        }
        log.info("[SMS:msg91] Sent to {}", recipient);
        return true;
    }

    /** Twilio Messages API. Requires account sid, auth token, and a from-number. */
    private boolean sendViaTwilio(String to, String message) {
        if (twilioSid == null || twilioSid.isBlank() || twilioToken == null || twilioToken.isBlank()) {
            log.warn("[SMS:twilio] Missing credentials — falling back to log.");
            return sendViaLog(to, message);
        }
        String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioSid + "/Messages.json";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(twilioSid, twilioToken);

        org.springframework.util.MultiValueMap<String, String> form =
            new org.springframework.util.LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", twilioFrom);
        form.add("Body", message);

        HttpEntity<org.springframework.util.MultiValueMap<String, String>> req = new HttpEntity<>(form, headers);
        restTemplate.postForEntity(url, req, String.class);
        log.info("[SMS:twilio] Sent to {}", to);
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Normalise an Indian mobile to E.164 (+91XXXXXXXXXX). Returns null if invalid. */
    private String normalise(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+91") && digits.length() == 13) return digits;
        if (digits.startsWith("91") && digits.length() == 12)  return "+" + digits;
        if (digits.length() == 10 && digits.matches("^[6-9]\\d{9}$")) return "+91" + digits;
        if (digits.startsWith("+") && digits.length() >= 11) return digits; // other countries
        return null;
    }
}
