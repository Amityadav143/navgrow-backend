/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.controller;

import com.navgrow.entity.Rfq;
import com.navgrow.entity.RfqItem;
import com.navgrow.enums.RfqStatus;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.RfqRepository;
import com.navgrow.service.EmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * RfqController — B2B Request-For-Quote workflow.
 *
 * Public:   POST /rfqs                  (submit an RFQ — guest or logged-in)
 *           GET  /rfqs/track/{number}   (track by RFQ number, no auth)
 * Buyer:    GET  /rfqs/mine             (my RFQs)
 *           POST /rfqs/{id}/accept      (accept a quote)
 *           POST /rfqs/{id}/reject      (reject a quote)
 * Admin:    GET  /rfqs                  (list, filter by status)
 *           GET  /rfqs/{id}             (detail)
 *           PUT  /rfqs/{id}/quote       (price the RFQ + send quote)
 *           PATCH /rfqs/{id}/status     (update status)
 */
@RestController
@RequestMapping("/rfqs")
@RequiredArgsConstructor
@Slf4j
public class RfqController {

    private final RfqRepository repo;
    private final EmailService emailService;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── DTOs ──────────────────────────────────────────────────────────────────
    @Data
    public static class RfqItemReq {
        private UUID productId;
        @NotBlank private String productName;
        private String sku;
        @NotNull @Min(1) private Integer quantity;
        private String specification;
        private Integer gstRate;
    }

    @Data
    public static class RfqReq {
        @NotBlank private String buyerName;
        @Email @NotBlank private String buyerEmail;
        @NotBlank private String buyerPhone;
        private String company;
        private String gstin;
        private String deliveryCity;
        private String deliveryState;
        private String pincode;
        private String notes;
        @NotEmpty private List<RfqItemReq> items;
    }

    @Data
    public static class QuoteLineReq {
        @NotNull private UUID itemId;
        @NotNull private BigDecimal unitPrice;
        private Integer gstRate;
    }

    @Data
    public static class QuoteReq {
        @NotEmpty private List<QuoteLineReq> lines;
        private BigDecimal shipping;
        private String adminMessage;
        private String paymentTerms;
        private Integer validityDays;   // default 15
    }

    // ── Submit an RFQ (public) ────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody RfqReq req,
                                                       @AuthenticationPrincipal UserDetails ud) {
        Rfq rfq = Rfq.builder()
            .rfqNumber(generateRfqNumber())
            .buyerName(req.getBuyerName())
            .buyerEmail(req.getBuyerEmail())
            .buyerPhone(req.getBuyerPhone())
            .company(req.getCompany())
            .gstin(req.getGstin())
            .deliveryCity(req.getDeliveryCity())
            .deliveryState(req.getDeliveryState())
            .pincode(req.getPincode())
            .notes(req.getNotes())
            .status(RfqStatus.SUBMITTED)
            .build();

        for (RfqItemReq it : req.getItems()) {
            rfq.addItem(RfqItem.builder()
                .productId(it.getProductId())
                .productName(it.getProductName())
                .sku(it.getSku())
                .quantity(it.getQuantity())
                .specification(it.getSpecification())
                .gstRate(it.getGstRate() != null ? it.getGstRate() : 18)
                .build());
        }

        Rfq saved = repo.save(rfq);
        emailService.sendRfqAcknowledgement(saved.getBuyerEmail(), saved.getBuyerName(),
                saved.getRfqNumber(), saved.getItems().size());

        Map<String, String> body = new HashMap<>();
        body.put("rfqNumber", saved.getRfqNumber());
        body.put("id", saved.getId().toString());
        body.put("message", "RFQ submitted. We'll send a formal quote within 1 business day.");
        return ResponseEntity.status(201).body(body);
    }

    // ── Track by number (public) ──────────────────────────────────────────────
    @GetMapping("/track/{number}")
    public ResponseEntity<Rfq> track(@PathVariable String number) {
        Rfq rfq = repo.findByRfqNumber(number)
            .orElseThrow(() -> new ResourceNotFoundException("RFQ", number));
        return ResponseEntity.ok(rfq);
    }

    // ── My RFQs (buyer) ───────────────────────────────────────────────────────
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<Rfq>> mine(@AuthenticationPrincipal UserDetails ud,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(repo.findByBuyerEmailOrderByCreatedAtDesc(ud.getUsername(), p));
    }

    // ── Accept / reject quote (buyer) ─────────────────────────────────────────
    @PostMapping("/{id}/accept")
    public ResponseEntity<Map<String, String>> accept(@PathVariable UUID id) {
        Rfq rfq = getOr404(id);
        if (rfq.getStatus() != RfqStatus.QUOTED)
            return ResponseEntity.badRequest().body(Map.of("message", "Only a quoted RFQ can be accepted."));
        rfq.setStatus(RfqStatus.ACCEPTED);
        repo.save(rfq);
        return ResponseEntity.ok(Map.of("message", "Quote accepted. Our team will reach out to finalise the order."));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, String>> reject(@PathVariable UUID id,
                                                       @RequestBody(required = false) Map<String, String> body) {
        Rfq rfq = getOr404(id);
        rfq.setStatus(RfqStatus.REJECTED);
        if (body != null && body.get("reason") != null)
            rfq.setAdminMessage("Buyer rejected: " + body.get("reason"));
        repo.save(rfq);
        return ResponseEntity.ok(Map.of("message", "Quote rejected."));
    }

    // ── Admin: list ───────────────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<Rfq>> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) RfqStatus status) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Rfq> result = (status != null)
            ? repo.findByStatusOrderByCreatedAtDesc(status, p)
            : repo.findAll(p);
        return ResponseEntity.ok(result);
    }

    // ── Admin: detail ─────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Rfq> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(getOr404(id));
    }

    // ── Admin: price + send quote ─────────────────────────────────────────────
    @PutMapping("/{id}/quote")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Rfq> quote(@PathVariable UUID id, @Valid @RequestBody QuoteReq req) {
        Rfq rfq = getOr404(id);

        Map<UUID, RfqItem> byId = new HashMap<>();
        for (RfqItem it : rfq.getItems()) byId.put(it.getId(), it);

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;

        for (QuoteLineReq line : req.getLines()) {
            RfqItem it = byId.get(line.getItemId());
            if (it == null) continue;
            int gstRate = line.getGstRate() != null ? line.getGstRate()
                        : (it.getGstRate() != null ? it.getGstRate() : 18);
            BigDecimal lineTotal = line.getUnitPrice()
                .multiply(BigDecimal.valueOf(it.getQuantity()));
            it.setQuotedUnitPrice(line.getUnitPrice());
            it.setLineTotal(lineTotal);
            it.setGstRate(gstRate);
            subtotal = subtotal.add(lineTotal);
            gstTotal = gstTotal.add(lineTotal.multiply(BigDecimal.valueOf(gstRate))
                        .divide(BigDecimal.valueOf(100)));
        }

        BigDecimal shipping = req.getShipping() != null ? req.getShipping() : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(gstTotal).add(shipping);
        int validity = req.getValidityDays() != null ? req.getValidityDays() : 15;

        rfq.setQuotedSubtotal(subtotal);
        rfq.setQuotedGst(gstTotal);
        rfq.setQuotedShipping(shipping);
        rfq.setQuotedTotal(total);
        rfq.setQuoteValidUntil(LocalDateTime.now().plusDays(validity));
        rfq.setAdminMessage(req.getAdminMessage());
        rfq.setPaymentTerms(req.getPaymentTerms());
        rfq.setStatus(RfqStatus.QUOTED);
        rfq.setQuotedAt(LocalDateTime.now());

        Rfq saved = repo.save(rfq);
        emailService.sendRfqQuoted(saved.getBuyerEmail(), saved.getBuyerName(),
                saved.getRfqNumber(), total.toPlainString(),
                saved.getQuoteValidUntil().toLocalDate().toString());

        return ResponseEntity.ok(saved);
    }

    // ── Admin: update status ──────────────────────────────────────────────────
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Rfq> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        Rfq rfq = getOr404(id);
        if (body.get("status") != null)
            rfq.setStatus(RfqStatus.valueOf(body.get("status")));
        if (body.get("assignedTo") != null)
            rfq.setAssignedTo(body.get("assignedTo"));
        return ResponseEntity.ok(repo.save(rfq));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Rfq getOr404(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("RFQ", id.toString()));
    }

    private String generateRfqNumber() {
        String day = LocalDateTime.now().format(DAY);
        // Sequence based on count for the day-ish; collision-safe enough with random suffix
        long n = (repo.count() % 10000) + 1;
        return String.format("RFQ-%s-%04d", day, n);
    }
}
