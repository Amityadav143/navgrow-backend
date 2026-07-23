/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.service;

import com.navgrow.entity.DeliveryZone;
import com.navgrow.repository.DeliveryZoneRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Works out whether an address can be served, what delivery costs there, and
 * roughly when it will arrive.
 *
 * Zone selection is longest-prefix-wins: '734' (Siliguri) beats '73' (North
 * Bengal) beats '7' (East region), so a specific rule always overrides a broad
 * one. Where two prefixes are the same length, the higher priority wins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryZoneRepository zoneRepo;

    /** What the storefront needs to render a serviceability answer. */
    @Data @Builder
    public static class DeliveryQuote {
        private boolean serviceable;
        private String  pincode;
        private String  zone;
        private String  note;

        private BigDecimal standardCharge;
        private Integer    etaMinDays;
        private Integer    etaMaxDays;
        private String     estimatedBy;      // human-readable date range

        private boolean    freeDelivery;
        private BigDecimal freeAbove;        // null when the zone never offers free
        private BigDecimal addForFreeDelivery;

        private boolean    expressAvailable;
        private BigDecimal expressCharge;
        private Integer    expressEtaDays;
        private String     expressBy;

        private boolean    codAvailable;
        private BigDecimal codCharge;
    }

    /** True only for a well-formed Indian pincode (no leading zero). */
    public boolean isValidPincode(String pincode) {
        return pincode != null && pincode.trim().matches("^[1-9][0-9]{5}$");
    }

    /**
     * Finds the zone whose prefix matches the pincode most specifically.
     * Returns empty when nothing matches at all.
     */
    public Optional<DeliveryZone> resolveZone(String pincode) {
        if (!isValidPincode(pincode)) return Optional.empty();
        final String pin = pincode.trim();

        record Match(DeliveryZone zone, int prefixLength) {}

        return zoneRepo.findByActiveTrue().stream()
            .flatMap(z -> Arrays.stream(
                        z.getPincodePrefixes() == null ? new String[0]
                                                       : z.getPincodePrefixes().split(","))
                    .map(String::trim)
                    .filter(p -> !p.isEmpty() && pin.startsWith(p))
                    .map(p -> new Match(z, p.length())))
            // Longest prefix first, then explicit priority.
            .max(Comparator.<Match>comparingInt(Match::prefixLength)
                    .thenComparingInt(m -> m.zone().getPriority() == null ? 0 : m.zone().getPriority()))
            .map(Match::zone);
    }

    /**
     * Builds a full quote for a pincode at a given order value.
     *
     * @param orderValue taxable order value, used for the free-delivery threshold.
     *                   Pass zero when the buyer is only checking serviceability.
     */
    public DeliveryQuote quote(String pincode, BigDecimal orderValue) {
        final BigDecimal value = orderValue == null ? BigDecimal.ZERO : orderValue;

        if (!isValidPincode(pincode)) {
            return DeliveryQuote.builder()
                    .serviceable(false).pincode(pincode)
                    .note("Enter a valid 6-digit pincode.")
                    .build();
        }

        DeliveryZone zone = resolveZone(pincode).orElse(null);
        if (zone == null) {
            return DeliveryQuote.builder()
                    .serviceable(false).pincode(pincode)
                    .note("We do not deliver to this pincode yet. Contact us and we will arrange a freight quotation.")
                    .build();
        }
        if (Boolean.FALSE.equals(zone.getServiceable())) {
            return DeliveryQuote.builder()
                    .serviceable(false).pincode(pincode).zone(zone.getName())
                    .note(zone.getNote() != null ? zone.getNote()
                          : "We do not deliver to this area yet.")
                    .build();
        }

        // Free above the zone's threshold. A threshold of zero means always free.
        BigDecimal freeAbove = zone.getFreeAbove();
        boolean free = freeAbove != null && value.compareTo(freeAbove) >= 0;
        BigDecimal charge = free ? BigDecimal.ZERO : zone.getBaseCharge();
        BigDecimal shortfall = (freeAbove != null && !free)
                ? freeAbove.subtract(value).max(BigDecimal.ZERO) : null;

        return DeliveryQuote.builder()
                .serviceable(true)
                .pincode(pincode)
                .zone(zone.getName())
                .note(zone.getNote())
                .standardCharge(charge)
                .etaMinDays(zone.getEtaMinDays())
                .etaMaxDays(zone.getEtaMaxDays())
                .estimatedBy(formatEta(zone.getEtaMinDays(), zone.getEtaMaxDays()))
                .freeDelivery(free)
                .freeAbove(freeAbove)
                .addForFreeDelivery(shortfall)
                .expressAvailable(Boolean.TRUE.equals(zone.getExpressAvailable()))
                .expressCharge(zone.getExpressCharge())
                .expressEtaDays(zone.getExpressEtaDays())
                .expressBy(Boolean.TRUE.equals(zone.getExpressAvailable()) && zone.getExpressEtaDays() != null
                           ? formatEta(zone.getExpressEtaDays(), zone.getExpressEtaDays()) : null)
                .codAvailable(Boolean.TRUE.equals(zone.getCodAvailable()))
                .codCharge(zone.getCodCharge())
                .build();
    }

    /**
     * Turns a working-day range into a readable date range.
     * Sundays are skipped — couriers here do not deliver on them, and a promise
     * that lands on a Sunday is a promise that gets missed.
     */
    private String formatEta(Integer minDays, Integer maxDays) {
        if (minDays == null || maxDays == null) return null;
        LocalDate from = addWorkingDays(LocalDate.now(), minDays);
        LocalDate to   = addWorkingDays(LocalDate.now(), maxDays);
        String f = fmt(from);
        return from.equals(to) ? f : f + " – " + fmt(to);
    }

    private LocalDate addWorkingDays(LocalDate start, int days) {
        LocalDate d = start;
        int added = 0;
        while (added < days) {
            d = d.plusDays(1);
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) added++;
        }
        return d;
    }

    private String fmt(LocalDate d) {
        String[] months = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};
        String[] dows   = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        return dows[d.getDayOfWeek().getValue() - 1] + ", " + d.getDayOfMonth() + " " + months[d.getMonthValue() - 1];
    }

    public List<DeliveryZone> allZones() {
        return zoneRepo.findAllByOrderByPriorityDescNameAsc();
    }
}
