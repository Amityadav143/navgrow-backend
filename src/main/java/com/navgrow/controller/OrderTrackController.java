package com.navgrow.controller;
import com.navgrow.entity.Order;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/orders") @RequiredArgsConstructor
public class OrderTrackController {
    private final OrderRepository repo;

    @GetMapping("/track/{orderNumber}")
    public ResponseEntity<Map<String, Object>> track(@PathVariable String orderNumber) {
        Order order = repo.findByOrderNumber(orderNumber.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        // Return safe public subset — no payment details
        return ResponseEntity.ok(Map.ofEntries(
            Map.entry("orderNumber",    order.getOrderNumber()),
            Map.entry("status",         order.getStatus()),
            Map.entry("paymentStatus",  order.getPaymentStatus()),
            Map.entry("grandTotal",     order.getGrandTotal()),
            Map.entry("items",          order.getItems()),
            Map.entry("customerName",   order.getCustomerName()),
            Map.entry("customerPhone",  order.getCustomerPhone()),
            Map.entry("addressLine1",   order.getAddressLine1()),
            Map.entry("addressLine2",   order.getAddressLine2() != null ? order.getAddressLine2() : ""),
            Map.entry("city",           order.getCity()),
            Map.entry("state",          order.getState()),
            Map.entry("pincode",        order.getPincode()),
            Map.entry("trackingNumber", order.getTrackingNumber() != null ? order.getTrackingNumber() : ""),
            Map.entry("courierName",    order.getCourierName() != null ? order.getCourierName() : ""),
            Map.entry("createdAt",      order.getCreatedAt())
        ));
    }

    @GetMapping("/mine")
    public ResponseEntity<?> myOrders(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails ud,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return ResponseEntity.ok(repo.findByCustomerEmailOrderByCreatedAtDesc(
            ud.getUsername(),
            org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending())));
    }
}
