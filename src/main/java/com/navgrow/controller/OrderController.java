/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 · navgrow.org
 */
package com.navgrow.controller;
import com.navgrow.entity.*;
import com.navgrow.enums.*;
import com.navgrow.exception.*;
import com.navgrow.repository.*;
import com.navgrow.service.EmailService;
import com.navgrow.util.OrderNumberGenerator;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {
    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final RazorpayClient razorpayClient;
    private final OrderNumberGenerator orderNumGen;
    private final EmailService emailService;
    private final com.navgrow.service.SmsService smsService;
    private final com.navgrow.service.InvoiceService invoiceService;

    @Value("${razorpay.key-secret}")
    private String razorpaySecret;

    @Value("${razorpay.currency}")
    private String currency;

    @Data
    public static class OrderItemReq {
        @NotNull UUID productId;
        @NotNull @Min(1) Integer quantity;
    }

    @Data
    public static class CreateOrderRequest {
        @NotBlank String customerName;
        @Email @NotBlank String customerEmail;
        @NotBlank String customerPhone;
        String companyName;
        String gstin;
        @NotBlank String addressLine1;
        String addressLine2;
        @NotBlank String city;
        @NotBlank String state;
        @NotBlank String pincode;
        String notes;
        @NotEmpty List<OrderItemReq> items;
    }

    @Data
    public static class PaymentVerifyRequest {
        @NotBlank String razorpayOrderId;
        @NotBlank String razorpayPaymentId;
        @NotBlank String razorpaySignature;
    }

    // ── Create Razorpay order ───────────────────────────────────────────────
    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        // Validate and build order items
        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstAmount = BigDecimal.ZERO;

        for (OrderItemReq itemReq : req.getItems()) {
            Product product = productRepo.findById(itemReq.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId().toString()));
            if (!product.isActive()) throw new BadRequestException("Product not available: " + product.getName());
            if (itemReq.getQuantity() == null || itemReq.getQuantity() < 1) {
                throw new BadRequestException("Invalid quantity for " + product.getName());
            }
            // Prevent overselling: reject if the requested quantity exceeds available stock.
            if (product.getStockQty() != null && itemReq.getQuantity() > product.getStockQty()) {
                throw new BadRequestException("Only " + product.getStockQty()
                    + " unit(s) of " + product.getName() + " are in stock");
            }

            BigDecimal lineTotal = product.getPrice()
                .multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
            // Each product can have its own GST slab (5/12/18/28%), so tax is summed per line.
            BigDecimal rate = product.getGstRate() != null ? product.getGstRate() : new BigDecimal("18");
            BigDecimal lineGst = lineTotal.multiply(rate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(lineTotal);
            gstAmount = gstAmount.add(lineGst);

            items.add(OrderItem.builder()
                .productName(product.getName())
                .product(product)
                .unitPrice(product.getPrice())
                .gstRate(rate)
                .quantity(itemReq.getQuantity())
                .subtotal(lineTotal)
                .build());
        }

        subtotal  = subtotal.setScale(2, RoundingMode.HALF_UP);
        gstAmount = gstAmount.setScale(2, RoundingMode.HALF_UP);
        // Shipping: free for orders of ₹5,000+ (by item subtotal), otherwise a flat ₹150.
        // This mirrors the cart/checkout display so the amount charged matches what the buyer saw.
        BigDecimal shipping = subtotal.compareTo(new BigDecimal("5000")) >= 0
            ? BigDecimal.ZERO : new BigDecimal("150");
        shipping = shipping.setScale(2, RoundingMode.HALF_UP);
        BigDecimal grandTotal = subtotal.add(gstAmount).add(shipping).setScale(2, RoundingMode.HALF_UP);

        // Create DB order
        Order order = Order.builder()
            .orderNumber(orderNumGen.generate())
            .customerName(req.getCustomerName()).customerEmail(req.getCustomerEmail())
            .customerPhone(req.getCustomerPhone()).companyName(req.getCompanyName())
            .gstin(req.getGstin())
            .addressLine1(req.getAddressLine1()).addressLine2(req.getAddressLine2())
            .city(req.getCity()).state(req.getState()).pincode(req.getPincode())
            .subtotal(subtotal).gstAmount(gstAmount)
            .shippingCharge(shipping).discountAmount(BigDecimal.ZERO)
            .grandTotal(grandTotal)
            .status(OrderStatus.PENDING).paymentStatus(PaymentStatus.PENDING)
            .notes(req.getNotes())
            .build();
        order.setItems(items);
        items.forEach(i -> i.setOrder(order));
        orderRepo.save(order);

        // Create Razorpay order
        try {
            long amountPaise = grandTotal.multiply(BigDecimal.valueOf(100)).longValue();
            JSONObject options = new JSONObject();
            options.put("amount", amountPaise);
            options.put("currency", currency);
            options.put("receipt", order.getOrderNumber());
            options.put("notes", new JSONObject()
                .put("order_id", order.getId().toString())
                .put("customer", req.getCustomerName())
                .put("email", req.getCustomerEmail()));

            com.razorpay.Order rzpOrder = razorpayClient.orders.create(options);
            order.setRazorpayOrderId(rzpOrder.get("id"));
            orderRepo.save(order);

            return ResponseEntity.status(201).body(Map.of(
                "orderId",        order.getId(),
                "orderNumber",    order.getOrderNumber(),
                "razorpayOrderId", rzpOrder.get("id"),
                "amount",         amountPaise,
                "currency",       currency,
                "grandTotal",     grandTotal
            ));
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new RuntimeException("Payment gateway error. Please try again.");
        }
    }

    // ── Verify payment ──────────────────────────────────────────────────────
    @PostMapping("/payment/verify")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> verifyPayment(@Valid @RequestBody PaymentVerifyRequest req) {
        Order order = orderRepo.findByRazorpayOrderId(req.getRazorpayOrderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found for Razorpay order: " + req.getRazorpayOrderId()));

        // Verify HMAC signature
        try {
            String payload = req.getRazorpayOrderId() + "|" + req.getRazorpayPaymentId();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpaySecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            String expectedSig = hex.toString();

            if (!expectedSig.equals(req.getRazorpaySignature())) {
                order.setPaymentStatus(PaymentStatus.FAILED);
                orderRepo.save(order);
                throw new BadRequestException("Payment verification failed");
            }

            order.setRazorpayPaymentId(req.getRazorpayPaymentId());
            order.setRazorpaySignature(req.getRazorpaySignature());
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepo.save(order);

            // Decrement stock for each purchased item now that payment has succeeded.
            // Done after payment (not at order creation) so abandoned/unpaid orders
            // don't hold inventory. Stock never goes below zero.
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null && product.getStockQty() != null) {
                    int remaining = product.getStockQty() - item.getQuantity();
                    product.setStockQty(Math.max(0, remaining));
                    productRepo.save(product);
                }
            }

            // Send confirmation email async
            emailService.sendOrderConfirmation(
                order.getCustomerEmail(), order.getCustomerName(),
                order.getOrderNumber(), order.getGrandTotal().toPlainString());

            // Send confirmation SMS (best-effort; never blocks the response)
            try {
                smsService.send(order.getCustomerPhone(),
                    "Your Navgrow order " + order.getOrderNumber() + " is confirmed. Total Rs " +
                    order.getGrandTotal().toPlainString() + ". Track it at navgrow.org. Thank you!");
            } catch (Exception ignored) { /* SMS must never break order confirmation */ }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "orderNumber", order.getOrderNumber(),
                "paymentId", req.getRazorpayPaymentId(),
                "message", "Payment verified. Order confirmed!"
            ));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Payment verification error: {}", e.getMessage());
            throw new RuntimeException("Payment verification failed");
        }
    }

    // ── User: my orders ─────────────────────────────────────────────────────
    @GetMapping("/mine")
    public ResponseEntity<Page<Order>> myOrders(
            @AuthenticationPrincipal UserDetails ud,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(
            orderRepo.findByCustomerEmailOrderByCreatedAtDesc(ud.getUsername(), pageable));
    }

    // ── Public order tracking (by order number, no auth) ─────────────────────
    @GetMapping("/track/{orderNumber}")
    public ResponseEntity<Map<String, Object>> track(@PathVariable String orderNumber) {
        Order order = orderRepo.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber));
        // Return only non-sensitive fields needed for tracking (no full customer PII).
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("orderNumber",    order.getOrderNumber());
        out.put("status",         order.getStatus());
        out.put("paymentStatus",  order.getPaymentStatus());
        out.put("grandTotal",     order.getGrandTotal());
        out.put("trackingNumber", order.getTrackingNumber());
        out.put("courierName",    order.getCourierName());
        out.put("createdAt",      order.getCreatedAt());
        return ResponseEntity.ok(out);
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────
    // ── GST tax invoice (print-ready HTML → browser saves as PDF) ─────────────
    @GetMapping(value = "/{orderNumber}/invoice", produces = "text/html")
    public ResponseEntity<String> invoice(@PathVariable String orderNumber,
                                          @RequestParam(value = "email", required = false) String email) {
        Order order = orderRepo.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new com.navgrow.exception.ResourceNotFoundException("Order", orderNumber));
        // The invoice carries full billing PII (name, address, phone, GSTIN), so —
        // unlike the deliberately PII-free /track endpoint — it must not be readable
        // by order number alone. The caller must also present the billing email.
        if (email == null || order.getCustomerEmail() == null
                || !email.trim().equalsIgnoreCase(order.getCustomerEmail().trim())) {
            return ResponseEntity.status(403)
                .header("Content-Type", "text/html; charset=UTF-8")
                .body("<html><body style='font-family:sans-serif;padding:40px;max-width:560px;margin:auto'>"
                    + "<h2>Verification needed</h2>"
                    + "<p>To protect your billing details, invoices can only be opened from the "
                    + "verified link in <strong>My Account &rarr; Orders</strong> on navgrow.org.</p>"
                    + "<p>Need help? Write to info@navgrow.org or call +91 89270 70972.</p>"
                    + "</body></html>");
        }
        // Only allow invoice once payment is captured
        if (order.getPaymentStatus() == null
                || order.getPaymentStatus() == com.navgrow.enums.PaymentStatus.PENDING) {
            return ResponseEntity.badRequest()
                .body("<html><body style='font-family:sans-serif;padding:40px'>"
                    + "<h2>Invoice not available yet</h2>"
                    + "<p>An invoice is generated once payment is confirmed.</p></body></html>");
        }
        String html = invoiceService.generateInvoiceHtml(order);
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(html);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Page<Order>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(status != null
            ? orderRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
            : orderRepo.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<Order> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", id.toString())));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Order> updateStatus(
            @PathVariable UUID id, @RequestParam OrderStatus status,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String courierName) {
        Order order = orderRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", id.toString()));
        order.setStatus(status);
        if (trackingNumber != null) order.setTrackingNumber(trackingNumber);
        if (courierName    != null) order.setCourierName(courierName);
        return ResponseEntity.ok(orderRepo.save(order));
    }
}
