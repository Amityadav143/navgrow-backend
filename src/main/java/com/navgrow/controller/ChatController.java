/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 · navgrow.org
 */
package com.navgrow.controller;

import com.navgrow.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    // ── Simple in-memory rate limiter: 30 requests per IP per hour ────────────
    private final Map<String, long[]> rateLimitMap = new ConcurrentHashMap<>();
    private static final int RATE_LIMIT    = 30;
    private static final long WINDOW_MS    = 3_600_000L; // 1 hour

    // ── DTOs ──────────────────────────────────────────────────────────────────
    @Data
    public static class MessageDto {
        @NotBlank
        private String role;    // "user" or "assistant"
        @NotBlank
        @Size(max = 4000)
        private String content;
    }

    @Data
    public static class ChatRequest {
        @NotEmpty
        @Size(max = 50)
        private List<MessageDto> messages;
    }

    // ── Main chat endpoint ────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, String>> chat(
            @Valid @RequestBody ChatRequest req,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);

        // Rate check
        if (isRateLimited(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                "reply", "You have sent too many messages. Please wait a few minutes before trying again, " +
                         "or contact us directly at info@navgrow.org."
            ));
        }

        // Validate: last message must be from user
        List<MessageDto> msgs = req.getMessages();
        if (msgs.isEmpty() || !"user".equals(msgs.get(msgs.size() - 1).getRole())) {
            return ResponseEntity.badRequest().body(Map.of("reply", "Invalid message format."));
        }

        // Sanitise: trim and cap
        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (MessageDto m : msgs) {
            apiMessages.add(Map.of(
                "role",    m.getRole().equals("assistant") ? "assistant" : "user",
                "content", m.getContent().strip()
            ));
        }

        log.debug("Chat from IP={} messages={}", ip, msgs.size());
        String reply = chatService.chat(apiMessages);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    // ── Suggested quick-reply starters (static — used by frontend) ────────────
    @GetMapping("/starters")
    public ResponseEntity<List<Map<String, String>>> starters() {
        return ResponseEntity.ok(List.of(
            Map.of("label", "🛠 View Services",       "text", "What engineering services do you offer?"),
            Map.of("label", "🛒 Browse Shop",          "text", "What products are available in your shop?"),
            Map.of("label", "📋 Get a Quote",          "text", "How can I get a project quote?"),
            Map.of("label", "📦 Track My Order",       "text", "How do I track my order?"),
            Map.of("label", "💼 Open Positions",       "text", "What job vacancies do you have?"),
            Map.of("label", "🏗 Our Projects",         "text", "Tell me about your completed projects"),
            Map.of("label", "📞 Contact Us",           "text", "What are your contact details?"),
            Map.of("label", "🎟 Discount Codes",       "text", "Do you have any coupon codes?")
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        rateLimitMap.compute(ip, (k, v) -> {
            if (v == null) return new long[]{now, 1};
            if (now - v[0] > WINDOW_MS) { v[0] = now; v[1] = 1; }
            else v[1]++;
            return v;
        });
        long[] entry = rateLimitMap.get(ip);
        return entry != null && entry[1] > RATE_LIMIT;
    }

    private String getClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
