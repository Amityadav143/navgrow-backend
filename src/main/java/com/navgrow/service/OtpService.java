/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OtpService — issues and verifies short-lived one-time passwords for phone login.
 *
 * Storage is in-memory with expiry (suitable for a single instance / development).
 * For production at scale, back this with Redis and integrate an SMS provider
 * (e.g. MSG91, Twilio) in {@link #deliver}.
 */
@Service
@Slf4j
public class OtpService {

    private static final long   TTL_MS      = 5 * 60 * 1000; // 5 minutes
    private static final int    MAX_ATTEMPTS = 5;
    private static final SecureRandom RNG    = new SecureRandom();

    private final SmsService smsService;

    public OtpService(SmsService smsService) {
        this.smsService = smsService;
    }

    private record Entry(String code, long expiresAt, int attempts) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** Generate, store, and deliver a 6-digit OTP for the given phone. */
    public void issue(String phone) {
        String code = String.format("%06d", RNG.nextInt(1_000_000));
        store.put(phone, new Entry(code, System.currentTimeMillis() + TTL_MS, 0));
        deliver(phone, code);
    }

    /** Verify a submitted OTP. Returns true on success and consumes the code. */
    public boolean verify(String phone, String submitted) {
        Entry e = store.get(phone);
        if (e == null) return false;
        if (System.currentTimeMillis() > e.expiresAt()) { store.remove(phone); return false; }
        if (e.attempts() >= MAX_ATTEMPTS) { store.remove(phone); return false; }
        if (e.code().equals(submitted)) {
            store.remove(phone);
            return true;
        }
        // wrong code — increment attempts
        store.put(phone, new Entry(e.code(), e.expiresAt(), e.attempts() + 1));
        return false;
    }

    /**
     * Deliver the OTP to the user via SMS. Falls back to a log entry when no SMS
     * provider is configured (sms.provider=log), so the flow is testable in
     * non-production environments. Delivery is best-effort and never throws.
     */
    private void deliver(String phone, String code) {
        boolean sent = smsService.sendOtp(phone, code);
        if (!sent) {
            log.warn("[OTP] SMS delivery not completed for {} — check SMS provider configuration.", phone);
        }
    }
}
