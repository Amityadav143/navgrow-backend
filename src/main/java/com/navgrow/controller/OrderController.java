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
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        // Validate and build order items
        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItemReq itemReq : req.getItems()) {
            Product product = productRepo.findById(itemReq.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId().toString()));
            if (!product.isActive()) throw new BadRequestException("Product not available: " + product.getName());

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(lineTotal);

            items.add(OrderItem.builder()
                .productName(product.getName())
                .product(product)
                .unitPrice(product.getPrice())
                .gstRate(product.getGstRate())
                .quantity(itemReq.getQuantity())
                .subtotal(lineTotal)
                .build());
        }

        BigDecimal gstAmount = subtotal.multiply(new BigDecimal("0.18"));
        BigDecimal grandTotal = subtotal.add(gstAmount);

        // Create DB order
        Order order = Order.builder()
            .orderNumber(orderNumGen.generate())
            .customerName(req.getCustomerName()).customerEmail(req.getCustomerEmail())
            .customerPhone(req.getCustomerPhone()).companyName(req.getCompanyName())
            .gstin(req.getGstin())
            .addressLine1(req.getAddressLine1()).addressLine2(req.getAddressLine2())
            .city(req.getCity()).state(req.getState()).pincode(req.getPincode())
            .subtotal(subtotal).gstAmount(gstAmount)
            .shippingCharge(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
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

            // Send confirmation email async
            emailService.sendOrderConfirmation(
                order.getCustomerEmail(), order.getCustomerName(),
                order.getOrderNumber(), order.getGrandTotal().toPlainString());

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

    // ── Admin endpoints ─────────────────────────────────────────────────────
    // ── GST tax invoice (print-ready HTML → browser saves as PDF) ─────────────
    @GetMapping(value = "/{orderNumber}/invoice", produces = "text/html")
    public ResponseEntity<String> invoice(@PathVariable String orderNumber) {
        Order order = orderRepo.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new com.navgrow.exception.ResourceNotFoundException("Order", orderNumber));
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
