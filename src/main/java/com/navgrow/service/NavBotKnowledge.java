/*
 * © 2024–2026 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform
 */
package com.navgrow.service;

import java.util.*;

/**
 * NavBotKnowledge — Navgrow's in-house assistant brain.
 *
 * This is a self-contained, rule-based answer engine so the chatbot can handle
 * the large majority of real customer questions WITHOUT depending on any external
 * LLM (Claude/GPT). Each {@link Intent} carries weighted keywords and a curated,
 * accurate answer. A query is scored against every intent and the best match
 * wins, so the most relevant topic is chosen rather than the first keyword hit.
 *
 * Answers are kept factually aligned with the live product (delivery model, GST
 * invoicing, coupons, services, etc.). When nothing scores highly enough, the
 * caller can either return the guided fallback or defer to the optional LLM.
 */
public final class NavBotKnowledge {

    /** A single answerable topic. */
    public static final class Intent {
        final String id;
        final String answer;
        /** keyword -> weight (higher = stronger signal for this intent) */
        final Map<String, Integer> keywords;

        Intent(String id, String answer, Map<String, Integer> keywords) {
            this.id = id; this.answer = answer; this.keywords = keywords;
        }
    }

    /** Result of a lookup: the chosen answer plus the score/confidence. */
    public static final class Match {
        public final String intentId;
        public final String answer;
        public final int score;
        public final List<String> followUps;
        Match(String intentId, String answer, int score, List<String> followUps) {
            this.intentId = intentId; this.answer = answer; this.score = score;
            this.followUps = followUps;
        }
    }

    // Contextual "you might also ask" chips per intent — keeps the conversation
    // flowing and surfaces capabilities the user may not know to ask about.
    private static final Map<String, List<String>> FOLLOW_UPS = new HashMap<>();
    static {
        FOLLOW_UPS.put("services", List.of("Do you work with Indian Railways?", "How do I get a quote?", "Which industries do you serve?"));
        FOLLOW_UPS.put("products", List.of("Is a GST invoice provided?", "What are the delivery charges?", "Can I place a bulk order?"));
        FOLLOW_UPS.put("quote", List.of("I need a bulk / RFQ quote", "What details do you need?", "How long does a quotation take?"));
        FOLLOW_UPS.put("rfq_bulk", List.of("Do you sell on GeM / IREPS?", "Are you MSME registered?", "How do I share my requirement?"));
        FOLLOW_UPS.put("delivery", List.of("How do I track my order?", "Do you deliver to my city?", "Is COD available?"));
        FOLLOW_UPS.put("invoice", List.of("How do I add my GSTIN?", "Where do I download my invoice?", "What are your payment options?"));
        FOLLOW_UPS.put("railway", List.of("What is your RDSO experience?", "Tell me about shed works", "How do I request a quote?"));
        FOLLOW_UPS.put("sustainability", List.of("Tell me about solar solutions", "Do you build STP/ETP plants?", "What is rainwater harvesting?"));
        FOLLOW_UPS.put("solar", List.of("Do you help with net-metering?", "What about AMC for solar?", "Get a site assessment"));
        FOLLOW_UPS.put("company", List.of("Why choose Navgrow?", "Where do you operate?", "How can I contact you?"));
        FOLLOW_UPS.put("payment", List.of("Is a GST invoice provided?", "What are delivery charges?", "Is COD available?"));
        FOLLOW_UPS.put("account", List.of("How do I reset my password?", "How do I track an order?", "Where are my invoices?"));
        FOLLOW_UPS.put("returns", List.of("What is the warranty?", "How do I contact support?", "Track my order"));
        FOLLOW_UPS.put("greeting", List.of("What services do you offer?", "Show me products", "How do I get a quote?"));
        FOLLOW_UPS.put("why_navgrow", List.of("What are your certifications?", "Which industries do you serve?", "Get a quote"));
        FOLLOW_UPS.put("international", List.of("What products do you offer?", "How do I request a quote?", "Talk to the team"));
        FOLLOW_UPS.put("process_timeline", List.of("How do I get a quote?", "Do you offer AMC?", "Contact the team"));
    }

    // Score at or above which we consider the built-in answer confident enough.
    public static final int CONFIDENCE_THRESHOLD = 2;

    private static final List<Intent> INTENTS = new ArrayList<>();

    private static Map<String, Integer> kw(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(((String) pairs[i]).toLowerCase(), (Integer) pairs[i + 1]);
        }
        return m;
    }

    private static void add(String id, String answer, Map<String, Integer> keywords) {
        INTENTS.add(new Intent(id, answer, keywords));
    }

    static {
        // ── Greeting / smalltalk ──────────────────────────────────────────────
        add("greeting",
            "Hello! 👋 I'm **NavBot**, the Navgrow Engineering assistant. I can help with:\n\n" +
            "• Our engineering & sustainability **services**\n" +
            "• The **online shop** — products, prices, GST invoices, delivery\n" +
            "• **Quotes, bulk orders (RFQ)** and tenders\n" +
            "• **Order tracking**, account & payment help\n" +
            "• **Contact** details and company info\n\n" +
            "What can I help you with today?",
            kw("hi", 3, "hello", 3, "hey", 3, "good morning", 3, "good afternoon", 3,
               "namaste", 3, "namaskar", 3, "who are you", 3, "what can you do", 3, "help", 1, "start", 1,
               // Hindi / Bengali greetings (native + romanized)
               "नमस्ते", 3, "नमस्कार", 3, "हैलो", 3, "নমস্কার", 3, "হ্যালো", 3,
               "kaise ho", 3, "kemon achen", 3, "assalamualaikum", 3, "hola", 2, "bonjour", 2));

        // ── Services overview ─────────────────────────────────────────────────
        add("services",
            "Navgrow delivers **ten engineering & sustainability service lines**:\n\n" +
            "🚂 **Railway Infrastructure** — track, ROB, electrification, signalling, shed works (RDSO standards)\n" +
            "🏭 **Industrial Engineering** — fabrication, plant engineering & installations\n" +
            "🏗️ **Civil & Construction** — buildings, bridges, roads, structural works\n" +
            "📄 **Government Contracts** — tender execution via GeM & IREPS\n" +
            "🔧 **Maintenance & AMC** — preventive/corrective upkeep, 24/7\n" +
            "💧 **Rainwater Harvesting** — recharge & storage systems\n" +
            "☀️ **Solar Energy** — rooftop & ground-mount PV\n" +
            "♻️ **Wastewater Treatment** — STP/ETP & zero-liquid-discharge\n" +
            "⚡ **Energy Efficiency & Audits** — retrofits that cut cost & carbon\n" +
            "🌿 **Green Building** — efficient, certified, low-impact builds\n\n" +
            "See details: **navgrow.org/services** — or tell me your requirement for a quote.",
            kw("service", 3, "services", 3, "what do you do", 3, "what do you offer", 3,
               "capabilities", 2, "offerings", 2, "solutions", 1,
               "सेवा", 3, "सेवाएं", 3, "क्या करते हो", 3, "সেবা", 3, "কি করেন", 3,
               "seva", 3, "servicio", 3, "services offerts", 2));

        // ── Sustainability focus ──────────────────────────────────────────────
        add("sustainability",
            "Sustainability is built into our engineering. We provide:\n\n" +
            "💧 **Rainwater Harvesting** — recharge structures, storage, water audits\n" +
            "☀️ **Solar Energy** — rooftop/ground-mount PV, net-metering\n" +
            "♻️ **Wastewater Treatment** — STP/ETP, recycling, ZLD compliance\n" +
            "⚡ **Energy Efficiency** — audits, retrofits, carbon assessment\n" +
            "🌿 **Green Building** — IGBC/GRIHA-aligned, low-impact design\n\n" +
            "Learn more: **navgrow.org/services**",
            kw("sustainability", 3, "sustainable", 3, "solar", 3, "rainwater", 3,
               "wastewater", 3, "water treatment", 3, "stp", 3, "etp", 3, "zld", 3,
               "green building", 3, "energy audit", 3, "renewable", 2, "environment", 2, "carbon", 2));

        // ── Railway ───────────────────────────────────────────────────────────
        add("railway",
            "We specialise in **Indian Railways infrastructure & rolling-stock support**:\n\n" +
            "• **Track & ROB** — construction and renewal\n" +
            "• **Electrification (OHE)** and signalling & telecom\n" +
            "• **Shed works** — diesel/electric loco shed construction & renovation\n" +
            "• **Testing plants** — e.g. rainwater leakage testing for electric locos\n" +
            "• **Rolling-stock mods** — RDSO-compliant hand-brake fitment, Wabtec lube-oil storage\n\n" +
            "All work follows RDSO specifications and Indian Railways vendor norms.\n" +
            "Discuss a project: **navgrow.org/contact**",
            kw("railway", 3, "rail", 2, "loco", 3, "locomotive", 3, "shed", 3, "rdso", 3,
               "wabtec", 3, "ohe", 3, "electrification", 3, "signalling", 3, "track", 2, "irctc", 1));

        // ── Shop / products ───────────────────────────────────────────────────
        add("products",
            "Our **B2B Engineering Shop** stocks ISI-certified safety gear, railway tools and PPE. Popular items:\n\n" +
            "• **Industrial Safety Helmet (ISI)** — ₹480\n" +
            "• **High-Visibility Safety Vest** — from ₹320\n" +
            "• **Steel-Toe Safety Boots** — ₹2,400\n" +
            "• **Digital Torque Wrench** — ₹4,800\n" +
            "• **Ultrasonic Rail Flaw Detector** — ₹28,000\n" +
            "• **FR Coverall, gloves, respirators, calipers, thermometers** and more\n\n" +
            "Every order includes a **GST invoice with HSN codes**. Browse: **navgrow.org/shop**",
            kw("product", 3, "products", 3, "shop", 3, "buy", 3, "purchase", 3, "catalogue", 3,
               "catalog", 3, "helmet", 2, "gloves", 2, "boots", 2, "wrench", 2, "ppe", 3,
               "safety equipment", 3, "tools", 2, "vest", 2, "coverall", 2, "respirator", 2,
               "उत्पाद", 3, "खरीद", 3, "सामान", 2, "পণ্য", 3, "কিনতে", 3, "kharid", 3, "producto", 3));

        // ── Pricing / quotes ──────────────────────────────────────────────────
        add("quote",
            "You can get pricing two ways:\n\n" +
            "🛒 **Products** — prices are listed on each item at **navgrow.org/shop** (shown GST-inclusive).\n" +
            "📋 **Services & bulk orders** — use the instant estimator at **navgrow.org/quote-calculator**, " +
            "or send drawings/BOQ to **info@navgrow.org**. You'll get a formal, GST-compliant quotation, " +
            "typically **within 24 business hours**.\n\n" +
            "For anything urgent, call **+91 89270 70972**.",
            kw("quote", 3, "quotation", 3, "price", 2, "pricing", 3, "cost", 2, "estimate", 3,
               "budget", 2, "rate", 2, "how much", 3, "boq", 2));

        // ── RFQ / bulk / tenders ──────────────────────────────────────────────
        add("rfq_bulk",
            "For **bulk / institutional orders and tenders**:\n\n" +
            "• Raise a **Request for Quotation (RFQ)** from the shop or bulk-enquiry form and we'll send " +
            "institutional pricing, usually within 24 hours.\n" +
            "• We execute **government & PSU work via GeM and IREPS** with full documentation.\n" +
            "• As a **DPIIT-recognised, Udyam MSME**, we qualify for MSME procurement preferences.\n\n" +
            "Send requirements to **info@navgrow.org** or start at **navgrow.org/quote-calculator**.",
            kw("rfq", 4, "bulk", 4, "wholesale", 4, "tender", 4, "gem", 3, "ireps", 3,
               "institutional", 3, "quantity", 2, "large order", 4, "procurement", 2, "government order", 3, "bulk order", 5));

        // ── Delivery / shipping (accurate to the per-zone model) ──────────────
        add("delivery",
            "**Delivery & charges** for shop orders:\n\n" +
            "• Enter your **PIN code** on the product or cart page to see the exact delivery charge and ETA.\n" +
            "• **Siliguri:** free delivery. **Other locations:** charged by zone (per product × quantity).\n" +
            "• Typical timelines: **3–5 business days** pan-India; **7–10 days** to remote North-East areas.\n\n" +
            "Delivery is calculated transparently before you pay — no surprises at checkout.",
            kw("delivery", 3, "shipping", 3, "ship", 2, "dispatch", 2, "courier", 2,
               "delivery charge", 3, "delivery time", 3, "how long", 2, "pin code", 3, "pincode", 3));

        // ── Order tracking ────────────────────────────────────────────────────
        add("track_order",
            "To **track your order**:\n\n" +
            "1. Go to **navgrow.org/track-order**\n" +
            "2. Enter your **order number** (in your confirmation email) and email\n" +
            "3. You'll see the current status and dispatch details\n\n" +
            "Logged in? Your orders and **GST invoices** are under **My Account → My Orders**.",
            kw("track", 3, "tracking", 3, "order status", 3, "where is my order", 3,
               "my order", 2, "order number", 2));

        // ── GST invoice ───────────────────────────────────────────────────────
        add("invoice",
            "**GST invoices** are generated for **every order** — including Cash-on-Delivery.\n\n" +
            "• Download from **My Account → My Orders → GST Invoice** once the order is confirmed.\n" +
            "• Invoices include your **HSN codes and a full tax breakdown**, suitable for input-tax-credit claims.\n" +
            "• Need your GSTIN on the invoice? Add it under **My Account → Company / GST** before ordering.\n\n" +
            "Trouble finding an invoice? Email **info@navgrow.org** with your order number.",
            kw("invoice", 3, "gst", 3, "bill", 2, "billing", 2, "tax", 2, "hsn", 3,
               "gstin", 3, "input tax", 2, "receipt", 2));

        // ── Payment ───────────────────────────────────────────────────────────
        add("payment",
            "**Payment options** at checkout:\n\n" +
            "• **Online** — cards, UPI, net-banking and wallets via Razorpay (secure)\n" +
            "• **Cash on Delivery (COD)** — pay when your order arrives\n\n" +
            "All prices are shown **GST-inclusive** with a clear tax breakdown. Your payment details are " +
            "processed securely and never stored on our servers.",
            kw("payment", 3, "pay", 2, "razorpay", 3, "upi", 3, "card", 2, "netbanking", 2,
               "net banking", 2, "cod", 3, "cash on delivery", 3, "wallet", 2));

        // ── Coupons ───────────────────────────────────────────────────────────
        add("coupon",
            "**Discounts:** apply a coupon code in your cart at checkout.\n\n" +
            "• **NAVGROW10** — 10% off (up to ₹250) on orders ≥ ₹3,000, once per customer\n\n" +
            "One code per order. Any active offers show on the shop and at checkout. 🛒 **navgrow.org/shop**",
            kw("coupon", 3, "discount", 3, "promo", 3, "offer", 2, "code", 2, "voucher", 2, "deal", 2));

        // ── Returns / warranty ────────────────────────────────────────────────
        add("returns",
            "**Returns & warranty:**\n\n" +
            "• Products can be returned within **7 days** of delivery if unused and in original condition.\n" +
            "• Manufacturing defects are covered per each product's warranty terms.\n" +
            "• To start a return or claim, email **info@navgrow.org** with your order number and photos.\n\n" +
            "We'll guide you through replacement or refund promptly.",
            kw("return", 3, "returns", 3, "refund", 3, "warranty", 3, "replace", 2,
               "replacement", 2, "defect", 2, "damaged", 2, "exchange", 2));

        // ── Account / password ────────────────────────────────────────────────
        add("account",
            "**Account help:**\n\n" +
            "• **Register / sign in** from the top-right of the site (email or Google).\n" +
            "• **Forgot password?** Use the *Forgot password* link on the sign-in screen — we'll email a reset link.\n" +
            "• **Change password** anytime under **My Account → Password**.\n" +
            "• Manage addresses, company/GST details and saved quotes from your dashboard.\n\n" +
            "Still stuck? Email **info@navgrow.org**.",
            kw("account", 3, "login", 3, "log in", 3, "sign in", 3, "register", 3, "sign up", 3,
               "password", 3, "reset password", 3, "forgot", 3, "profile", 2));

        // ── Careers ───────────────────────────────────────────────────────────
        add("careers",
            "We're growing! Open roles are listed at **navgrow.org/careers** (engineering, site supervision, " +
            "tendering, HSE, e-commerce and more — mostly Siliguri-based).\n\n" +
            "Apply on the careers page or send your CV to **info@navgrow.org**.",
            kw("job", 3, "jobs", 3, "career", 3, "careers", 3, "vacancy", 3, "hiring", 3,
               "apply", 2, "position", 2, "recruitment", 3, "internship", 2, "work with", 2));

        // ── Certifications / company ──────────────────────────────────────────
        add("company",
            "**About Navgrow Engineering Service Pvt. Ltd.**\n\n" +
            "• Incorporated **2022**, headquartered in **Siliguri, West Bengal**\n" +
            "• **DPIIT Startup India** recognised · **Udyam MSME** registered · **Make in India**\n" +
            "• Works to **Indian Railways / RDSO** vendor norms; 100% on-time delivery track record\n" +
            "• CIN: U74999WB2022PTC256012\n\n" +
            "More: **navgrow.org/about**",
            kw("about", 2, "company", 3, "who is navgrow", 3, "certification", 3, "certif", 3,
               "dpiit", 3, "msme", 3, "udyam", 3, "make in india", 3, "registered", 2,
               "cin", 2, "established", 2, "history", 2));

        // ── Contact ───────────────────────────────────────────────────────────
        add("contact",
            "**Reach Navgrow Engineering:**\n\n" +
            "📧 **Email:** info@navgrow.org\n" +
            "📱 **Phone / WhatsApp:** +91 89270 70972\n" +
            "🏢 **Office:** Ward No-47, Old Matigara Road, Pati Colony, Siliguri, WB – 734001\n" +
            "⏰ **Hours:** Mon–Fri, 9 AM – 6 PM IST\n\n" +
            "💬 Fastest response on WhatsApp: **wa.me/918927070972**",
            kw("contact", 3, "phone", 3, "email", 3, "address", 3, "office", 3, "location", 3,
               "reach", 2, "call", 2, "whatsapp", 3, "number", 2, "where are you", 3,
               "संपर्क", 3, "फोन", 3, "पता", 2, "যোগাযোগ", 3, "ফোন", 3, "sampark", 3, "contacto", 3, "contacter", 3));

        // ── Thanks / closing ──────────────────────────────────────────────────
        add("thanks",
            "You're welcome! 😊 If there's anything else — products, a quote, an order, or company info — " +
            "just ask. For direct help: **info@navgrow.org** / **+91 89270 70972**.",
            kw("thank", 3, "thanks", 3, "thank you", 3, "appreciate", 2, "great", 1, "awesome", 1));

        // ── Solar (specific) ──────────────────────────────────────────────────
        add("solar",
            "**Solar Energy Solutions** from Navgrow:\n\n" +
            "• **Rooftop & ground-mount PV** for factories, institutions and commercial buildings\n" +
            "• **On-grid, off-grid and hybrid** systems with battery storage\n" +
            "• **Net-metering** support and subsidy/DISCOM paperwork guidance\n" +
            "• Design, supply, installation and **AMC** under one roof\n\n" +
            "Cleaner power and lower bills. Get a site assessment: **navgrow.org/quote-calculator**.",
            kw("solar", 4, "photovoltaic", 4, "pv panel", 4, "rooftop solar", 5, "net metering", 4,
               "solar power", 5, "solar panel", 5));

        // ── Water treatment (specific) ────────────────────────────────────────
        add("water_treatment",
            "**Wastewater Treatment & Recycling:**\n\n" +
            "• **STP** (sewage) and **ETP** (effluent) design, build, operate & maintain\n" +
            "• **Zero-Liquid-Discharge (ZLD)** systems for compliance\n" +
            "• **Rainwater harvesting** — recharge, storage and water audits\n" +
            "• Water reuse for industrial, commercial and landscaping needs\n\n" +
            "Systems built to meet environmental and discharge norms. 📋 **navgrow.org/services**",
            kw("stp", 4, "etp", 4, "zld", 5, "sewage", 4, "effluent", 4, "water treatment", 5,
               "wastewater", 4, "water recycling", 5, "rainwater harvesting", 5, "water audit", 4));

        // ── Industries served ─────────────────────────────────────────────────
        add("industries",
            "We serve four core customer groups:\n\n" +
            "🚂 **Indian Railways** — locomotive works, shed & testing-plant projects, track-side infrastructure\n" +
            "🏭 **Industrial plants** — fabrication, plant maintenance and engineered installations\n" +
            "🏛️ **Government departments** — tender-based execution via GeM and IREPS with full documentation\n" +
            "🏢 **Private developers** — civil works, solar and water systems for commercial premises\n\n" +
            "Tell me your sector and I'll point you to the right service.",
            kw("industries", 3, "sectors", 3, "who do you serve", 4, "clients", 3, "customers", 2,
               "who are your clients", 4, "which industries", 4));

        // ── Project timeline / process ────────────────────────────────────────
        add("process_timeline",
            "**How we run a project — from enquiry to handover:**\n\n" +
            "1. **Share your requirement** — drawings, BOQ or a brief (email, phone or the online calculator)\n" +
            "2. **Formal quotation** — GST-compliant, with scope, timeline and terms, typically within 24 hours\n" +
            "3. **Execution under supervision** — a named supervisor, agreed milestones and photo progress records\n" +
            "4. **Handover** — complete dossier, GST invoice and post-handover support (with AMC options)\n\n" +
            "Timelines depend on scope — we commit dates up front and track to them (100% on-time record).",
            kw("timeline", 3, "how long does a project", 5, "process", 3, "how do you work", 4,
               "project duration", 5, "steps", 2, "workflow", 3, "how does it work", 3, "milestone", 2,
               "project take", 5, "how long will", 4, "complete the project", 4, "delivery time for project", 4));

        // ── AMC / maintenance (specific) ──────────────────────────────────────
        add("amc",
            "**Maintenance & Annual Maintenance Contracts (AMC):**\n\n" +
            "• **Preventive** — scheduled inspections and servicing to avoid breakdowns\n" +
            "• **Corrective** — rapid response to restore uptime\n" +
            "• Coverage for **electrical, mechanical, civil, plumbing and facility** systems\n" +
            "• **24/7 support** and tailored AMC plans to suit your budget\n\n" +
            "Keep assets running and costs predictable. Ask for an AMC quote: **info@navgrow.org**.",
            kw("amc", 4, "annual maintenance", 5, "maintenance contract", 5, "preventive maintenance", 4,
               "upkeep", 3, "servicing", 3, "facility management", 4));

        // ── Why choose Navgrow ────────────────────────────────────────────────
        add("why_navgrow",
            "**Why clients choose Navgrow:**\n\n" +
            "• **Quality-first** — ISI-marked materials, tested components, audit-ready documentation\n" +
            "• **Approved-vendor discipline** — trained to Indian Railways & departmental norms\n" +
            "• **One accountable partner** — design, supply, execution and maintenance under one roof\n" +
            "• **On-time, on-budget** — method-approved planning; 100% on-time delivery record\n" +
            "• **Sustainability built in** — solar, water-recycling and energy-efficiency expertise\n\n" +
            "Engineering excellence, delivered responsibly. 📋 **navgrow.org/about**",
            kw("why navgrow", 5, "why choose", 4, "why should", 4, "what makes you", 4,
               "advantage", 3, "different", 2, "better than", 3, "usp", 3, "strengths", 3));

        // ── Business hours ────────────────────────────────────────────────────
        add("hours",
            "**Business hours:** Monday–Friday, 9 AM – 6 PM IST.\n\n" +
            "You can browse the shop and place orders **24/7** at **navgrow.org/shop**, and we reply to " +
            "emails and WhatsApp within one business day. For anything urgent: **+91 89270 70972**.",
            kw("hours", 3, "timing", 3, "open", 2, "when are you open", 4, "working hours", 5,
               "business hours", 5, "office hours", 5, "what time", 3));

        // ── Warranty (specific) ───────────────────────────────────────────────
        add("warranty",
            "**Warranty on products:**\n\n" +
            "• Products carry the **manufacturer's warranty** stated on each item's page\n" +
            "• Genuine manufacturing defects are covered for repair or replacement\n" +
            "• Report an issue within the warranty window with your order number and photos\n\n" +
            "Email **info@navgrow.org** and we'll process the claim quickly.",
            kw("warranty", 4, "guarantee", 4, "warranty period", 5, "how long is the warranty", 5));

        // ── GST / B2B specifics ───────────────────────────────────────────────
        add("gst_b2b",
            "**For B2B / GST buyers:**\n\n" +
            "• Every order gets a proper **GST tax invoice with HSN codes** for input-tax-credit claims\n" +
            "• Add your **GSTIN** under *My Account → Company / GST* so it prints on the invoice\n" +
            "• Prices shown are **GST-inclusive** with a clear tax breakdown\n" +
            "• Need bulk/institutional pricing? Raise an **RFQ** and we'll respond within 24 hours\n\n" +
            "Compliant, audit-ready billing on every purchase.",
            kw("b2b", 4, "gstin", 4, "input tax credit", 5, "itc", 4, "business purchase", 4,
               "company gst", 5, "gst number", 4, "tax invoice", 4));

        // ── Location / service area ───────────────────────────────────────────
        add("service_area",
            "**Where we operate:**\n\n" +
            "• Headquartered in **Siliguri, West Bengal** — a strategic gateway to the Northeast, Nepal, " +
            "Bhutan and Bangladesh\n" +
            "• **Projects & services:** North Bengal and the North-East, expanding across India for larger works\n" +
            "• **Shop delivery:** pan-India (free in Siliguri; charged by zone elsewhere — enter your PIN for the exact rate)\n\n" +
            "Tell me your location and I'll confirm what we can do there.",
            kw("area", 2, "where do you operate", 4, "service area", 5, "do you cover", 4,
               "pan india", 3, "north east", 3, "siliguri", 3, "location", 2, "cover my city", 4));

        // ── International / global client enquiry ──────────────────────────────
        add("international",
            "Thanks for your interest from outside India! 🌍\n\n" +
            "Navgrow is based in **Siliguri, India** and works primarily across India, " +
            "with a gateway location to Nepal, Bhutan and Bangladesh. For **international " +
            "or cross-border requirements**, we handle enquiries case-by-case:\n\n" +
            "• Tell us your **country** and **requirement** (products or a project)\n" +
            "• We'll advise on **feasibility, shipping and duties** and share a tailored quote\n\n" +
            "📧 **info@navgrow.org** · 📱 **WhatsApp +91 89270 70972** — please include your " +
            "country and time zone and we'll respond within one business day.",
            kw("international", 4, "export", 4, "overseas", 4, "abroad", 4, "outside india", 5,
               "ship to", 3, "deliver to", 3, "worldwide", 4, "global", 3, "another country", 4,
               "from usa", 4, "from uk", 4, "from dubai", 4, "from nepal", 4, "from bangladesh", 4,
               "import", 3, "customs", 3, "duty", 2, "foreign", 3));

        // ── Human handoff ─────────────────────────────────────────────────────
        add("human",
            "Of course — I'll connect you with our team:\n\n" +
            "📱 **Call / WhatsApp:** +91 89270 70972 (fastest)\n" +
            "📧 **Email:** info@navgrow.org\n" +
            "📝 Or leave your details on **navgrow.org/contact** and we'll reach out.\n\n" +
            "Our team replies within one business day (Mon–Fri, 9 AM – 6 PM IST).",
            kw("talk to human", 5, "speak to someone", 5, "real person", 5, "agent", 3,
               "representative", 4, "customer care", 4, "customer support", 4, "call someone", 4,
               "talk to a person", 5, "human", 3));
    }

    /**
     * Score the query against every intent and return the best match, or null if
     * nothing meets the confidence threshold.
     */
    public static Match match(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        String q = " " + rawQuery.toLowerCase().trim() + " ";

        Intent best = null;
        int bestScore = 0;
        for (Intent intent : INTENTS) {
            int score = 0;
            for (Map.Entry<String, Integer> e : intent.keywords.entrySet()) {
                String k = e.getKey();
                // Word-ish containment: allow the keyword anywhere, but multi-word
                // phrases must appear contiguously (already handled by contains).
                if (q.contains(k)) {
                    score += e.getValue();
                    // Small bonus when the keyword is a standalone token.
                    if (q.contains(" " + k + " ")) score += 1;
                }
            }
            if (score > bestScore) { bestScore = score; best = intent; }
        }
        if (best == null || bestScore < CONFIDENCE_THRESHOLD) return null;
        return new Match(best.id, best.answer, bestScore,
            FOLLOW_UPS.getOrDefault(best.id, List.of()));
    }

    /** Guided fallback when no intent is confident and no LLM is used. */
    public static String guidedFallback() {
        return "I want to make sure I point you to the right place. I can help with:\n\n" +
            "• **Services** — “what services do you offer?”\n" +
            "• **Shop** — “show me products” or “what does a safety helmet cost?”\n" +
            "• **Quotes / bulk** — “I need a bulk quote”\n" +
            "• **Orders** — “track my order”, “where's my GST invoice?”\n" +
            "• **Company** — “are you MSME registered?”\n\n" +
            "Or reach a human directly: **info@navgrow.org** · **+91 89270 70972** · **wa.me/918927070972**.";
    }

    private NavBotKnowledge() {}
}
