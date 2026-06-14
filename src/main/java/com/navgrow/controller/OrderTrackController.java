/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
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

}
