/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * NavBot Chat Service.
 *
 * Answers are generated IN-HOUSE first via {@link NavBotKnowledge} — a
 * self-contained, scored intent engine that handles the large majority of real
 * customer questions with no external LLM, no per-message cost and no third-party
 * dependency. An external LLM (configured via {@code anthropic.api-key}) is used
 * only as an OPTIONAL fallback for questions the knowledge base can't confidently
 * answer; if no key is set, a guided in-house fallback is returned instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    @Value("${anthropic.api-key:sk-ant-placeholder-dev-key}")
    private String apiKey;

    @Value("${anthropic.api-url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;

    @Value("${anthropic.model:claude-opus-4-5}")
    private String model;

    @Value("${anthropic.max-tokens:1200}")
    private int maxTokens;

    private final RestTemplate restTemplate;

    // ── Test mode detection ────────────────────────────────────────────────────
    private boolean isTestMode() {
        return apiKey == null
            || apiKey.isBlank()
            || apiKey.equalsIgnoreCase("TEST")
            || apiKey.startsWith("sk-ant-test-")
            || apiKey.contains("your-key")
            || apiKey.contains("your_key");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYSTEM PROMPT
    // ═══════════════════════════════════════════════════════════════════════════
    private static final String SYSTEM_PROMPT = """
You are **NavBot**, the official AI assistant for Navgrow Engineering Service Pvt. Ltd.
You are expert, friendly, concise, and always helpful. You represent the brand professionally.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COMPANY PROFILE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Name:         Navgrow Engineering Service Pvt. Ltd.
CIN:          U74999WB2022PTC256012
Status:       DPIIT Recognised Startup | MSME Registered | Make in India
Founded:      2022  |  Mission: "Quality First!"
Address:      Ward No-47, Old Matigara Road, Pati Colony, Siliguri, West Bengal – 734001
Email:        info@navgrow.org
Phone:        +91 89270 70972
WhatsApp:     https://wa.me/918927070972
Website:      https://navgrow.org

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WHO WE ARE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Navgrow is a VERSATILE, MULTI-SECTOR engineering firm. We began with Indian Railways
projects — where precision and compliance are non-negotiable — and that discipline now
powers our work in sustainability. When asked what we do, lead with our SUSTAINABILITY
services — rainwater harvesting, solar energy, water recycling, energy efficiency, EV
charging, and green-building consulting. Mention our engineering heritage (railways,
industrial) as the proven foundation that makes us a reliable sustainability partner.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CORE SERVICES (Sustainability Solutions)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. RAINWATER HARVESTING
   • Rooftop and surface runoff harvesting systems
   • Groundwater recharge structures (CGWA-compliant)
   • Storage, filtration, water audits, and AMC

2. SOLAR ENERGY SOLUTIONS
   • Rooftop, ground-mount, and hybrid solar PV systems
   • Feasibility, net-metering, and subsidy (MNRE) assistance
   • Battery storage, solar pumping, and O&M

3. WASTEWATER TREATMENT & RECYCLING
   • Sewage (STP) and Effluent (ETP) Treatment Plants
   • Water recycling, reuse, and Zero Liquid Discharge support
   • CPCB/SPCB pollution-control compliance

4. ENERGY EFFICIENCY & AUDITS
   • BEE-aligned energy audits and retrofits
   • LED, HVAC, motor, and power-factor optimisation
   • IoT energy monitoring and carbon footprint assessment

5. GREEN BUILDING & SUSTAINABILITY CONSULTING
   • IGBC / GRIHA / LEED certification advisory
   • Sustainable design, environmental compliance, ESG reporting

ENGINEERING HERITAGE (our proven foundation)
   • Railway engineering (loco modification, testing plants — NER Zone, Wabtec)
   • Industrial fabrication and civil works
   • Government tender management (GeM, IREPS) and maintenance/AMC

OTHER CAPABILITIES
   • Safety audits, HSE management, training
   • IoT-based asset monitoring & reporting dashboards
   • Standards-aligned (RDSO/ISO) quality assurance

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
B2B SHOP — navgrow.org/shop
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Payment: Razorpay (UPI, Cards, Net Banking, Wallets) and Cash on Delivery
Delivery: FREE within Siliguri; elsewhere charged by zone (enter PIN code for the
          exact rate). Typical: 3–5 business days pan-India; 7–10 days to remote
          North-East areas. International enquiries: handled case-by-case — share
          your country and requirement and the team will advise on feasibility,
          shipping and duties.
GST invoice: issued for EVERY order (including COD), with HSN codes for input-tax
          credit. Add your GSTIN under My Account → Company/GST before ordering.
Returns: 7 days for unused items in original condition; manufacturing defects
          covered per each product's warranty.
Catalogue: 20 ISI/BIS-certified products across safety gear, railway tools,
          instruments and PPE.

DISCOUNT CODES (only mention codes you are certain are active; if unsure, tell the
customer to check the shop/checkout for current offers):
• NAVGROW10 — 10% off (up to ₹250) on orders ≥ ₹3,000, once per customer

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SERVING NATIONAL & GLOBAL CLIENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- Navgrow is Siliguri-based and serves clients across India, with a strategic
  gateway location to the North-East, Nepal, Bhutan and Bangladesh.
- For international / cross-border enquiries, be welcoming and helpful: confirm we
  can discuss the requirement, ask for their country and scope, and route them to
  info@navgrow.org or WhatsApp for a tailored response. Do NOT promise specific
  international shipping costs or timelines — offer to have the team advise.
- Prices are in INR (₹). If a user references another currency, give the INR
  figure and suggest they confirm the live conversion; do not invent exchange rates.
- Office hours are Mon–Fri, 9 AM – 6 PM IST; set expectations for replies across
  time zones (the shop and this assistant are available 24/7).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE GUIDELINES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- LANGUAGE: reply in the SAME language the user writes in. Support English, Hindi,
  Bengali, and other major languages (Spanish, French, Arabic, etc.) for global
  clients. If unsure of the language, default to clear, simple English.
- Be concise and skimmable (2–4 sentences for simple questions); use short bullet
  lists for multi-part answers.
- Be warm, professional and confident — you represent the brand to serious B2B and
  government buyers.
- Always end with a relevant next step: a link (navgrow.org/…), the WhatsApp line,
  or an offer to connect them with the team.
- NEVER invent prices, discount codes, certifications, specifications, delivery
  costs, exchange rates, or project details. If you don't know, say so and point
  to the team — accuracy is more important than sounding complete.
- LEAD CAPTURE: when a user shows genuine purchase/project intent, politely ask for
  their name, phone/email and a one-line requirement so the team can follow up.
- SAFETY: politely decline anything unrelated to Navgrow, unethical, or outside
  your scope, and steer back to how Navgrow can help.
""";

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN CHAT METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    public String chat(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return fallbackResponse();

        // Last user message drives intent detection.
        String userText = messages.stream()
            .filter(m -> "user".equals(m.get("role")))
            .reduce((a, b) -> b)
            .map(m -> m.getOrDefault("content", ""))
            .orElse("");

        // 1) IN-HOUSE FIRST — answer common questions directly, no external LLM,
        //    no per-message cost, no third-party dependency.
        NavBotKnowledge.Match match = NavBotKnowledge.match(userText);
        if (match != null) {
            log.debug("[NavBot] In-house answer for intent '{}' (score {})", match.intentId, match.score);
            return match.answer;
        }

        // 2) OPTIONAL LLM FALLBACK — only for questions the knowledge base can't
        //    confidently handle, and only if a real API key is configured.
        if (llmEnabled()) {
            try {
                return callLlm(messages);
            } catch (Exception e) {
                log.warn("[NavBot] LLM fallback failed, using guided fallback: {}", e.getMessage());
                return NavBotKnowledge.guidedFallback();
            }
        }

        // 3) NO LLM CONFIGURED — return the guided, self-contained fallback.
        return NavBotKnowledge.guidedFallback();
    }

    /** True only when a genuine external LLM key is configured (optional). */
    private boolean llmEnabled() {
        return !isTestMode();
    }

    /** Optional external-LLM call — used only when the in-house engine defers. */
    private String callLlm(List<Map<String, String>> messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            // Keep only the most recent turns so replies stay fast and costs stay
            // predictable, while preserving enough context for a coherent chat.
            List<Map<String, String>> trimmed = messages;
            final int MAX_TURNS = 12;
            if (messages.size() > MAX_TURNS) {
                trimmed = new ArrayList<>(messages.subList(messages.size() - MAX_TURNS, messages.size()));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("temperature", 0.4); // warm but consistent & on-brand
            body.put("system", SYSTEM_PROMPT);
            body.put("messages", trimmed);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            var response = restTemplate.exchange(apiUrl, HttpMethod.POST, request, Map.class);

            var data = response.getBody();
            if (data == null) return fallbackResponse();

            @SuppressWarnings("unchecked")
            var content = (List<Map<?, ?>>) data.get("content");
            if (content == null || content.isEmpty()) return fallbackResponse();

            var text = content.get(0).get("text");
            return text != null && !text.toString().isBlank() ? text.toString().trim() : fallbackResponse();

        } catch (HttpClientErrorException e) {
            int status = e.getStatusCode().value();
            log.error("Anthropic API error {}: {}", status, e.getMessage());
            if (status == 429) return rateLimitResponse();
            if (status == 401) return "My AI service needs reconfiguration. Please contact info@navgrow.org.";
            return fallbackResponse();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            log.error("Anthropic connection error: {}", e.getMessage());
            return fallbackResponse();
        } catch (Exception e) {
            log.error("Unexpected chat error: {}", e.getMessage());
            return fallbackResponse();
        }
    }

    private String fallbackResponse() {
        return "I'm having trouble connecting right now. Please reach us directly:\n\n" +
               "📧 info@navgrow.org\n" +
               "📱 +91 89270 70972\n" +
               "💬 wa.me/918927070972\n\n" +
               "We respond within 24 business hours.";
    }

    private String rateLimitResponse() {
        return "You've sent many messages! Please wait a few minutes.\n\n" +
               "**Contact us directly:**\n• info@navgrow.org\n• +91 89270 70972";
    }
}
