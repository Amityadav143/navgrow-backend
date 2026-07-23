/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.controller;

import com.navgrow.entity.DeliveryZone;
import com.navgrow.exception.ResourceNotFoundException;
import com.navgrow.repository.DeliveryZoneRepository;
import com.navgrow.service.AuditService;
import com.navgrow.service.DeliveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Delivery serviceability.
 *
 * The public check is what powers the "deliver to" box on product pages and in
 * the cart — a buyer should learn a pincode is unserviceable before they fill in
 * an address, not after. Zone maintenance sits behind admin roles.
 */
@RestController
@RequestMapping("/delivery")
@RequiredArgsConstructor
@Slf4j
public class DeliveryController {

    private final DeliveryService delivery;
    private final DeliveryZoneRepository zoneRepo;
    private final AuditService audit;

    /** Public: can we deliver to this pincode, at what cost, and by when. */
    @GetMapping("/check")
    public ResponseEntity<DeliveryService.DeliveryQuote> check(
            @RequestParam String pincode,
            @RequestParam(required = false) BigDecimal orderValue) {
        return ResponseEntity.ok(delivery.quote(pincode, orderValue));
    }

    // ─────────────────────────── Admin ───────────────────────────

    @Data
    public static class ZoneReq {
        @NotBlank @Size(max = 120) String name;
        @NotBlank String pincodePrefixes;
        Boolean serviceable;
        BigDecimal baseCharge;
        BigDecimal freeAbove;
        Integer etaMinDays;
        Integer etaMaxDays;
        Boolean codAvailable;
        BigDecimal codCharge;
        Boolean expressAvailable;
        BigDecimal expressCharge;
        Integer expressEtaDays;
        Integer priority;
        @Size(max = 255) String note;
        Boolean active;
    }

    @GetMapping("/zones")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('EDITOR')")
    public ResponseEntity<List<DeliveryZone>> zones() {
        return ResponseEntity.ok(delivery.allZones());
    }

    @PostMapping("/zones")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<DeliveryZone> create(@Valid @RequestBody ZoneReq req) {
        DeliveryZone z = new DeliveryZone();
        apply(req, z);
        DeliveryZone saved = zoneRepo.save(z);
        audit.log("DELIVERY_ZONE_CREATE", "DeliveryZone", saved.getId().toString(), saved.getName());
        return ResponseEntity.status(201).body(saved);
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<DeliveryZone> update(@PathVariable UUID id, @Valid @RequestBody ZoneReq req) {
        DeliveryZone z = zoneRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryZone", id.toString()));
        apply(req, z);
        DeliveryZone saved = zoneRepo.save(z);
        audit.log("DELIVERY_ZONE_UPDATE", "DeliveryZone", id.toString(), saved.getName());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/zones/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!zoneRepo.existsById(id)) throw new ResourceNotFoundException("DeliveryZone", id.toString());
        zoneRepo.deleteById(id);
        audit.log("DELIVERY_ZONE_DELETE", "DeliveryZone", id.toString(), "deleted");
        return ResponseEntity.noContent().build();
    }

    /** Lets an admin confirm how a pincode resolves before saving a rule. */
    @GetMapping("/zones/test")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER') or hasRole('EDITOR')")
    public ResponseEntity<DeliveryService.DeliveryQuote> test(
            @RequestParam String pincode,
            @RequestParam(required = false) BigDecimal orderValue) {
        return ResponseEntity.ok(delivery.quote(pincode, orderValue));
    }

    private void apply(ZoneReq r, DeliveryZone z) {
        z.setName(r.getName().trim());
        z.setPincodePrefixes(r.getPincodePrefixes().replaceAll("\\s+", ""));
        if (r.getServiceable()      != null) z.setServiceable(r.getServiceable());
        if (r.getBaseCharge()       != null) z.setBaseCharge(r.getBaseCharge());
        z.setFreeAbove(r.getFreeAbove());
        if (r.getEtaMinDays()       != null) z.setEtaMinDays(r.getEtaMinDays());
        if (r.getEtaMaxDays()       != null) z.setEtaMaxDays(r.getEtaMaxDays());
        if (r.getCodAvailable()     != null) z.setCodAvailable(r.getCodAvailable());
        if (r.getCodCharge()        != null) z.setCodCharge(r.getCodCharge());
        if (r.getExpressAvailable() != null) z.setExpressAvailable(r.getExpressAvailable());
        if (r.getExpressCharge()    != null) z.setExpressCharge(r.getExpressCharge());
        z.setExpressEtaDays(r.getExpressEtaDays());
        if (r.getPriority()         != null) z.setPriority(r.getPriority());
        z.setNote(r.getNote());
        if (r.getActive()           != null) z.setActive(r.getActive());
    }
}
