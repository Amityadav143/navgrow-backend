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
 * TEST MODE: If ANTHROPIC_API_KEY starts with "sk-ant-test-" or equals "TEST",
 *            uses smart keyword-based canned responses (no real API call).
 *            Set ANTHROPIC_API_KEY=TEST in .env.local to run without a real key.
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
CORE SERVICES (6 domains)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. RAILWAY INFRASTRUCTURE
   • Loco modification & retrofitting for improved performance
   • Diesel loco shed construction & renovation
   • Rainwater leakage testing plant installation
   • Modified hand brake fitment & testing
   • Track infrastructure for Indian Railways (NER Zone specialist)
   • Wabtec locomotive services & lube oil storage solutions

2. GOVERNMENT CONTRACTS
   • End-to-end tender management (GeM Portal, IREPS, state portals)
   • Bid preparation, technical compliance, documentation
   • Contract execution for Indian Railways & PSUs

3. MAINTENANCE SERVICES
   • Scheduled preventive maintenance for railway assets
   • Emergency breakdown response (24/7 availability)
   • Annual Maintenance Contracts (AMC) for loco sheds

4. CONSULTING & ADVISORY
   • Railway infrastructure planning & feasibility studies
   • Regulatory advisory (RDSO, Commissioner of Railway Safety)
   • Project management & quality assurance

5. SAFETY & COMPLIANCE
   • RDSO-compliant safety audits
   • HSE management systems
   • Safety culture training & awareness programs

6. TECHNOLOGY SOLUTIONS
   • IoT-based monitoring for railway assets
   • Automated data logging & reporting dashboards
   • SCADA integration for loco sheds

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
B2B SHOP — navgrow.org/shop
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Payment: Razorpay (UPI, Cards, Net Banking)
Shipping: Free on orders ≥ ₹5,000 | 3–5 days pan-India
Returns: 7 days for manufacturing defects
20+ ISI/BIS certified products across 5 categories.

DISCOUNT CODES:
• NAVGROW10  — 10% off any order (max ₹500)
• FLAT200    — ₹200 off on orders ≥ ₹2,000
• RAILWAY15  — 15% off for railway department orders (max ₹1,000)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE GUIDELINES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- Match user's language (Hindi → reply Hindi, English → English)
- Be concise (2–4 sentences for simple questions)
- Always end with a relevant CTA link or contact
- Never invent prices, certifications, or project details
- For lead capture: ask name + phone when user shows purchase/project intent
""";

    // ═══════════════════════════════════════════════════════════════════════════
    // TEST MODE — smart keyword-based canned responses
    // ═══════════════════════════════════════════════════════════════════════════
    private static final Map<String, String> CANNED = new LinkedHashMap<>();

    static {
        CANNED.put("service",
            "Navgrow offers **6 engineering service domains**:\n\n" +
            "• Railway Infrastructure (loco modification, shed construction)\n" +
            "• Government Contracts (GeM, IREPS, tender management)\n" +
            "• Maintenance Services (scheduled + emergency, 24/7)\n" +
            "• Consulting & Advisory (RDSO compliance, project management)\n" +
            "• Safety & Compliance (HSE audits, training)\n" +
            "• Technology Solutions (IoT, SCADA, data logging)\n\n" +
            "📋 Get a quote: navgrow.org/quote-calculator");

        CANNED.put("product|shop|buy|order|equipment|safety|tool|glove|helmet|boot|wrench|gauge",
            "Our **B2B Engineering Shop** has 20+ ISI-certified products across 5 categories:\n\n" +
            "• **Safety Equipment** — Helmets (₹480), Gloves (₹650), Boots (₹2,400)\n" +
            "• **Railway Tools** — Torque Wrench (₹4,800), Track Gauge (₹3,200), Flaw Detector (₹28,000)\n" +
            "• **Maintenance Supplies** — Rail Grease, Anti-Rust Spray (₹380)\n" +
            "• **Testing & Inspection** — Vernier Calliper (₹1,650), Thermometer (₹3,200)\n" +
            "• **PPE & Workwear** — FR Coverall (₹3,800), Knee Pads (₹890)\n\n" +
            "🛒 Shop now: navgrow.org/shop | Free shipping ≥ ₹5,000");

        CANNED.put("quot|price|cost|estimate|budget|rate",
            "Here are our **indicative project quote ranges** (ex-GST):\n\n" +
            "• Railway Engineering: ₹2.5L – ₹25L+ per project\n" +
            "• Government / PSU Tenders: ₹1L – ₹20L per engagement\n" +
            "• Maintenance AMC: ₹60K – ₹3L per year\n" +
            "• Consulting & Audits: ₹25K – ₹1L\n" +
            "• Technology (IoT/SCADA): ₹50K – ₹5L\n\n" +
            "📋 For a detailed quote, use our calculator: navgrow.org/quote-calculator\n" +
            "Or call directly: **+91 89270 70972**");

        CANNED.put("track|order number|delivery|shipping|dispatch",
            "To track your Navgrow order:\n\n" +
            "• Visit: **navgrow.org/track-order**\n" +
            "• Enter your order number (format: NGO-YYYYMMDD-XXXX)\n" +
            "• Your order number is in your confirmation email\n\n" +
            "**Delivery timelines:**\n" +
            "• Pan-India: 3–5 business days\n" +
            "• North-East India: 7–10 business days\n" +
            "• Free shipping on orders ≥ ₹5,000\n\n" +
            "📦 Track: navgrow.org/track-order");

        CANNED.put("job|career|vacancy|hiring|apply|position|role",
            "Navgrow currently has **6 open positions**:\n\n" +
            "1. Junior Civil Engineer (1–3 yrs, Siliguri)\n" +
            "2. Mechanical Engineer – Loco Services (2–5 yrs, Siliguri)\n" +
            "3. Tender & Procurement Executive (2–4 yrs, Siliguri)\n" +
            "4. Safety Officer – HSE (3–6 yrs, Siliguri)\n" +
            "5. E-Commerce & Digital Marketing Executive (1–2 yrs, Siliguri)\n" +
            "6. Site Supervisor – Railways (2–4 yrs, Pan-India)\n\n" +
            "💼 Apply: navgrow.org/careers | Send CV: info@navgrow.org");

        CANNED.put("contact|phone|email|address|office|location|reach",
            "Here's how to reach **Navgrow Engineering**:\n\n" +
            "📧 **Email:** info@navgrow.org\n" +
            "📱 **Phone/WhatsApp:** +91 89270 70972\n" +
            "🏢 **Office:** Ward No-47, Old Matigara Road, Pati Colony,\n" +
            "   Siliguri, West Bengal – 734001\n" +
            "⏰ **Hours:** Mon–Fri, 9 AM – 6 PM IST\n\n" +
            "💬 WhatsApp us for the fastest response: wa.me/918927070972");

        CANNED.put("certif|dpiit|msme|make in india|registr|recognition",
            "Navgrow Engineering holds the following **registrations & certifications**:\n\n" +
            "🏆 **DPIIT Startup India** — Recognition No. on certificate\n" +
            "🏭 **MSME Registration** — Udyam Registration under Ministry of MSME\n" +
            "🇮🇳 **Make in India** — Registered vendor\n" +
            "🚂 **Indian Railways Empanelled** — Active vendor for NER Zone\n\n" +
            "All projects are executed per RDSO specifications and Indian Railways norms.\n" +
            "📄 View certificates: navgrow.org/about");

        CANNED.put("discount|coupon|code|offer|promo",
            "We have **3 active discount codes** for the shop:\n\n" +
            "🎟 **NAVGROW10** — 10% off any order (max ₹500 discount)\n" +
            "🎟 **FLAT200** — ₹200 off on orders ≥ ₹2,000 (limited uses)\n" +
            "🎟 **RAILWAY15** — 15% off for railway department orders (max ₹1,000)\n\n" +
            "Apply at checkout. One code per order. 🛒 Shop: navgrow.org/shop");

        CANNED.put("railway|loco|locomotive|shed|diesel|electric|rdso|wabtec",
            "Navgrow specialises in **Indian Railways infrastructure**, including:\n\n" +
            "• **Loco Modification** — Diesel & electric locomotive retrofitting\n" +
            "• **Shed Construction** — Diesel loco shed construction & renovation\n" +
            "• **Rainwater Testing Plants** — Leak detection for electric locos\n" +
            "• **Hand Brake Fitment** — RDSO-compliant modification\n" +
            "• **Wabtec Services** — Lube oil storage & maintenance for Wabtec locos\n\n" +
            "We are an active NER Zone vendor with 3 completed Indian Railways projects.\n" +
            "📋 Discuss your project: navgrow.org/contact");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN CHAT METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    public String chat(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) return fallbackResponse();

        // In test mode — use smart canned responses (no API call)
        if (isTestMode()) {
            log.info("[NavBot TEST MODE] Responding with canned response (set ANTHROPIC_API_KEY to use real Claude)");
            return testModeResponse(messages);
        }

        // Real Claude API call
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("system", SYSTEM_PROMPT);
            body.put("messages", messages);

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

    private String testModeResponse(List<Map<String, String>> messages) {
        // Get last user message
        String userText = messages.stream()
            .filter(m -> "user".equals(m.get("role")))
            .reduce((a, b) -> b)
            .map(m -> m.getOrDefault("content", "").toLowerCase())
            .orElse("");

        // Match against canned responses
        for (Map.Entry<String, String> entry : CANNED.entrySet()) {
            String[] keywords = entry.getKey().split("\\|");
            for (String kw : keywords) {
                if (userText.contains(kw.trim())) {
                    return entry.getValue() + "\n\n_(NavBot running in **test mode** — set ANTHROPIC_API_KEY in .env.local for real AI responses)_";
                }
            }
        }

        // Generic test fallback
        return "Hi! I'm **NavBot**, Navgrow's AI assistant (running in **test mode**).\n\n" +
               "I can answer questions about:\n" +
               "• Engineering services & project quotes\n" +
               "• Products & shop orders\n" +
               "• Order tracking & careers\n" +
               "• Contact details & certifications\n\n" +
               "Try asking: \"What services do you offer?\" or \"Tell me about your products!\"\n\n" +
               "_Set ANTHROPIC_API_KEY in your .env.local file to enable full Claude AI responses._";
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
