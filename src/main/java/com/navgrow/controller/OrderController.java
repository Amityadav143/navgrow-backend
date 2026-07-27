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
    private final com.navgrow.service.DeliveryService deliveryService;
    private final com.navgrow.repository.UserRepository userRepo;
    private final com.navgrow.repository.CouponRepository couponRepo;
    private final com.navgrow.repository.CouponRedemptionRepository couponRedemptionRepo;

    @Value("${razorpay.key-secret}")
    private String razorpaySecret;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

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
        /** 'standard' or 'express' — priced against the buyer's delivery zone. */
        String deliverySpeed;
        /** "ONLINE" (Razorpay) or "COD". Defaults to ONLINE. */
        String paymentMethod;
        /** Optional discount code (e.g. NAVGROW10) — re-validated server-side. */
        String couponCode;
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
    public ResponseEntity<Map<String, Object>> createOrder(
            @AuthenticationPrincipal UserDetails principal,@Valid @RequestBody CreateOrderRequest req) {
        // Validate and build order items
        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal gstAmount = BigDecimal.ZERO;
        // Each line's own per-unit delivery base (product override) + quantity, so
        // heavier products can carry a higher shipping base than light ones.
        java.util.List<com.navgrow.service.DeliveryService.DeliveryLine> deliveryLines = new ArrayList<>();

        for (OrderItemReq itemReq : req.getItems()) {
            Product product = productRepo.findById(itemReq.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.getProductId().toString()));
            if (!product.isActive()) throw new BadRequestException("Product not available: " + product.getName());
            if (itemReq.getQuantity() == null || itemReq.getQuantity() < 1) {
                throw new BadRequestException("Invalid quantity for " + product.getName());
            }
            deliveryLines.add(new com.navgrow.service.DeliveryService.DeliveryLine(
                product.getDeliveryCharge(), itemReq.getQuantity()));
            // Prevent overselling: reject if the requested quantity exceeds available stock.
            if (product.getStockQty() != null && itemReq.getQuantity() > product.getStockQty()) {
                throw new BadRequestException("Only " + product.getStockQty()
                    + " unit(s) of " + product.getName() + " are in stock");
            }

            // Catalogue prices are GST-INCLUSIVE. The tax is therefore extracted
            // from the price rather than added on top:
            //     taxable = inclusive x 100 / (100 + rate)
            //     gst     = inclusive - taxable
            // Adding it on top would charge the customer more than the price shown.
            BigDecimal lineInclusive = product.getPrice()
                .multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
            // Each product can have its own GST slab (5/12/18/28%), so tax is worked out per line.
            BigDecimal rate = product.getGstRate() != null ? product.getGstRate() : new BigDecimal("18");
            BigDecimal lineTaxable = lineInclusive
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal("100").add(rate), 2, RoundingMode.HALF_UP);
            BigDecimal lineGst = lineInclusive.subtract(lineTaxable).setScale(2, RoundingMode.HALF_UP);

            // subtotal carries the taxable value so the invoice reconciles:
            // taxable + gst == the inclusive price the customer was shown.
            subtotal = subtotal.add(lineTaxable);
            gstAmount = gstAmount.add(lineGst);
            BigDecimal lineTotal = lineInclusive;

            items.add(OrderItem.builder()
                .productName(product.getName())
                .product(product)
                .unitPrice(product.getPrice())
                .hsnCode(product.getHsnCode())
                .gstRate(rate)
                .quantity(itemReq.getQuantity())
                .subtotal(lineTaxable)
                .build());
        }

        subtotal  = subtotal.setScale(2, RoundingMode.HALF_UP);
        gstAmount = gstAmount.setScale(2, RoundingMode.HALF_UP);
        // Delivery is priced against the buyer's zone — the same calculation the
        // checkout showed them. A flat national rule here would charge a figure
        // the customer was never quoted (free in Siliguri, chargeable elsewhere).
        // Delivery is charged PER PRODUCT LINE and scales with each line's own
        // quantity: base × lineQty × slab(lineQty), summed over lines. The zone
        // quote gives the raw per-unit base charge for the pincode; we apply the
        // per-product formula here so the charge matches the cart/checkout preview.
        var deliveryQuote = deliveryService.quote(req.getPincode(), subtotal);
        if (!deliveryQuote.isServiceable()) {
            throw new BadRequestException(deliveryQuote.getNote() != null
                ? deliveryQuote.getNote()
                : "We do not deliver to pincode " + req.getPincode() + " yet.");
        }
        boolean express = "express".equalsIgnoreCase(req.getDeliverySpeed())
                          && deliveryQuote.isExpressAvailable();
        BigDecimal baseCharge = express
            ? (deliveryQuote.getExpressCharge()  != null ? deliveryQuote.getExpressCharge()  : BigDecimal.ZERO)
            : (deliveryQuote.getStandardCharge() != null ? deliveryQuote.getStandardCharge() : BigDecimal.ZERO);
        // Free zones (Siliguri) return a zero base and stay free.
        boolean freeZone = baseCharge == null || baseCharge.signum() <= 0;
        BigDecimal shipping = com.navgrow.service.DeliveryService
            .perProductDelivery(baseCharge, deliveryLines, freeZone)
            .setScale(2, RoundingMode.HALF_UP);

        // Cash on delivery is only offered where the zone allows it — the same
        // rule the storefront showed the buyer. Asking for COD into a zone that
        // does not support it is rejected rather than silently downgraded.
        boolean cod = "COD".equalsIgnoreCase(req.getPaymentMethod());
        if (cod && !deliveryQuote.isCodAvailable()) {
            throw new BadRequestException(
                "Cash on delivery is not available for pincode " + req.getPincode() + ". Please pay online.");
        }
        BigDecimal codCharge = cod && deliveryQuote.getCodCharge() != null
            ? deliveryQuote.getCodCharge().setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Orders belong to an account: the endpoint is authenticated, so the
        // buyer can find this order under "My orders" rather than only by number.
        // Resolved before the coupon because "once per customer" is keyed on the
        // account, not the email typed into the form.
        User buyer = principal != null
            ? userRepo.findByEmail(principal.getUsername()).orElse(null) : null;

        // ── Coupon ──────────────────────────────────────────────────────────
        // Re-validated on the server against the price the customer actually pays
        // (goods INCLUSIVE of GST) — the client-sent figure is never trusted. The
        // discount is applied as a single deduction and shown as its own line on
        // the invoice, so per-line taxable values still reconcile.
        BigDecimal goodsInclusive = subtotal.add(gstAmount);
        BigDecimal discount = BigDecimal.ZERO;
        Coupon appliedCoupon = null;
        String couponCode = req.getCouponCode() != null ? req.getCouponCode().trim() : null;
        if (couponCode != null && !couponCode.isEmpty()) {
            Coupon coupon = couponRepo.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new BadRequestException("Invalid coupon code."));
            if (!coupon.isValid())
                throw new BadRequestException("Coupon " + coupon.getCode() + " is expired or no longer valid.");
            if (goodsInclusive.compareTo(coupon.getMinOrderAmount()) < 0)
                throw new BadRequestException("Coupon " + coupon.getCode()
                    + " applies only to orders of ₹" + coupon.getMinOrderAmount().toBigInteger() + " or more.");
            if (buyer == null)
                throw new BadRequestException("Please sign in to use a coupon.");
            if (couponRedemptionRepo.existsByCouponIdAndUserId(coupon.getId(), buyer.getId()))
                throw new BadRequestException("You have already used " + coupon.getCode() + ". This code is limited to one order per customer.");
            discount = coupon.calculateDiscount(goodsInclusive);
            appliedCoupon = coupon;
            couponCode = coupon.getCode();
        } else {
            couponCode = null;
        }

        BigDecimal grandTotal = goodsInclusive.subtract(discount)
            .add(shipping).add(codCharge).setScale(2, RoundingMode.HALF_UP);

        Order order = Order.builder()
            .orderNumber(orderNumGen.generate())
            .user(buyer)
            .customerName(req.getCustomerName()).customerEmail(req.getCustomerEmail())
            .customerPhone(req.getCustomerPhone()).companyName(req.getCompanyName())
            .gstin(req.getGstin())
            .addressLine1(req.getAddressLine1()).addressLine2(req.getAddressLine2())
            .city(req.getCity()).state(req.getState()).pincode(req.getPincode())
            .deliveryZone(deliveryQuote.getZone())
            .deliverySpeed(express ? "express" : "standard")
            .deliveryEtaMin(express ? deliveryQuote.getExpressEtaDays() : deliveryQuote.getEtaMinDays())
            .deliveryEtaMax(express ? deliveryQuote.getExpressEtaDays() : deliveryQuote.getEtaMaxDays())
            .subtotal(subtotal).gstAmount(gstAmount)
            .shippingCharge(shipping).discountAmount(discount)
            .couponCode(couponCode)
            .codCharge(codCharge).paymentMethod(cod ? "COD" : "ONLINE")
            .grandTotal(grandTotal)
            .status(cod ? OrderStatus.CONFIRMED : OrderStatus.PENDING)
            .paymentStatus(PaymentStatus.PENDING)
            .notes(req.getNotes())
            .build();
        order.setItems(items);
        items.forEach(i -> i.setOrder(order));
        orderRepo.save(order);

        // A COD order is complete at this point — there is nothing to collect
        // online. Stock is committed now because the order is already confirmed,
        // unlike a prepaid order which holds no inventory until payment clears.
        if (cod) {
            // COD orders are confirmed immediately, so the coupon is spent now.
            recordCouponRedemption(appliedCoupon, buyer, order);
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null && product.getStockQty() != null) {
                    product.setStockQty(Math.max(0, product.getStockQty() - item.getQuantity()));
                    productRepo.save(product);
                }
            }
            emailService.sendOrderConfirmation(
                order.getCustomerEmail(), order.getCustomerName(),
                order.getOrderNumber(), order.getGrandTotal().toPlainString());
            // Alert the office inbox so fulfilment can start immediately.
            emailService.sendNewOrderAdminNotification(order);
            try {
                smsService.send(order.getCustomerPhone(),
                    "Your Navgrow order " + order.getOrderNumber() + " is confirmed (Cash on Delivery). Amount payable Rs "
                    + order.getGrandTotal().toPlainString());
            } catch (Exception e) {
                log.warn("COD confirmation SMS failed for {}: {}", order.getOrderNumber(), e.getMessage());
            }
            log.info("COD order placed: {} for {}", order.getOrderNumber(), order.getGrandTotal());
            return ResponseEntity.status(201).body(Map.of(
                "orderId",       order.getId(),
                "orderNumber",   order.getOrderNumber(),
                "paymentMethod", "COD",
                "codCharge",     codCharge,
                "grandTotal",    grandTotal
            ));
        }

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
                // Return the SAME key that created this order, so the checkout
                // widget can never be initialised with a mismatched key (which
                // makes Razorpay report "The id provided does not exist").
                "razorpayKeyId",  razorpayKeyId,
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

        // Idempotency: Razorpay can retry the callback and users can double-submit.
        // A replay would pass the signature check again, so without this guard the
        // stock would be decremented twice and the customer would get duplicate
        // confirmations. If the payment is already recorded, just acknowledge it.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "orderNumber", order.getOrderNumber(),
                "message", "Payment already verified. Order confirmed!"
            ));
        }

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

            // Spend the coupon only now that payment has actually cleared, so an
            // abandoned/unpaid online order never burns the customer's one use.
            if (order.getCouponCode() != null && order.getUser() != null) {
                couponRepo.findByCodeIgnoreCase(order.getCouponCode())
                    .ifPresent(c -> recordCouponRedemption(c, order.getUser(), order));
            }

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

            // Alert the office inbox so fulfilment can start immediately.
            emailService.sendNewOrderAdminNotification(order);

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
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Order> updateStatus(
            @PathVariable UUID id, @RequestParam OrderStatus status,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String courierName) {
        Order order = orderRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order", id.toString()));

        OrderStatus previous = order.getStatus();
        boolean statusChanged = previous != status;

        // Stock was committed for this order if payment cleared (online) or it is a
        // confirmed COD order (COD commits stock at placement). If such an order is
        // now being CANCELLED, put the reserved units back so inventory isn't lost.
        boolean stockWasCommitted =
            order.getPaymentStatus() == PaymentStatus.PAID
            || ("COD".equalsIgnoreCase(order.getPaymentMethod())
                && previous != OrderStatus.PENDING && previous != OrderStatus.CANCELLED);
        if (status == OrderStatus.CANCELLED && previous != OrderStatus.CANCELLED && stockWasCommitted) {
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null && product.getStockQty() != null) {
                    product.setStockQty(product.getStockQty() + item.getQuantity());
                    productRepo.save(product);
                }
            }
            log.info("Restored stock for cancelled order {}", order.getOrderNumber());
        }

        order.setStatus(status);
        if (trackingNumber != null) order.setTrackingNumber(trackingNumber);
        if (courierName    != null) order.setCourierName(courierName);
        Order saved = orderRepo.save(order);

        // Keep the customer informed on every real status change — the shipped /
        // delivered / cancelled updates people expect. Best-effort: a failed
        // notification must not fail the admin's update.
        if (statusChanged) {
            emailService.sendOrderStatusUpdate(saved);
            try {
                smsService.send(saved.getCustomerPhone(), buildStatusSms(saved));
            } catch (Exception e) {
                log.warn("Status SMS failed for {}: {}", saved.getOrderNumber(), e.getMessage());
            }
        }
        return ResponseEntity.ok(saved);
    }

    /** Short SMS body for an order status change. */
    private String buildStatusSms(Order order) {
        String num = order.getOrderNumber();
        return switch (order.getStatus()) {
            case SHIPPED -> "Your Navgrow order " + num + " has shipped"
                + (order.getTrackingNumber() != null && !order.getTrackingNumber().isBlank()
                    ? " (Tracking: " + order.getTrackingNumber() + ")" : "")
                + ". Track at navgrow.org.";
            case DELIVERED -> "Your Navgrow order " + num + " has been delivered. Thank you for shopping with us!";
            case CANCELLED -> "Your Navgrow order " + num + " has been cancelled. Any online payment will be refunded.";
            case PROCESSING -> "Your Navgrow order " + num + " is being prepared for dispatch.";
            case REFUNDED -> "A refund has been issued for your Navgrow order " + num + ".";
            default -> "Update on your Navgrow order " + num + ": " + order.getStatus() + ".";
        };
    }

    /**
     * Writes the (coupon, customer) redemption row and bumps the coupon's usage
     * counter — but only once per customer. The UNIQUE (coupon_id, user_id)
     * constraint is the real guard, so catching its violation means a retry or a
     * concurrent request can never turn a duplicate into a 500.
     */
    private void recordCouponRedemption(Coupon coupon, User buyer, Order order) {
        if (coupon == null || buyer == null) return;
        if (couponRedemptionRepo.existsByCouponIdAndUserId(coupon.getId(), buyer.getId())) return;
        try {
            couponRedemptionRepo.save(CouponRedemption.builder()
                .couponId(coupon.getId())
                .userId(buyer.getId())
                .orderId(order.getId())
                .couponCode(coupon.getCode())
                .build());
            coupon.setUsageCount(coupon.getUsageCount() + 1);
            couponRepo.save(coupon);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            log.info("Coupon {} already redeemed by user {} (race) — ignoring duplicate",
                coupon.getCode(), buyer.getId());
        }
    }
}
