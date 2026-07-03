/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 */
package com.navgrow.repository;

import com.navgrow.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, UUID> {

    /** Count of each event since a cutoff, for funnel totals. */
    @Query("SELECT e.eventName, COUNT(e) FROM AnalyticsEvent e " +
           "WHERE e.createdAt >= :since GROUP BY e.eventName")
    List<Object[]> countByEventSince(@Param("since") LocalDateTime since);

    /** Distinct sessions that fired a given event since a cutoff (funnel width). */
    @Query("SELECT COUNT(DISTINCT e.sessionId) FROM AnalyticsEvent e " +
           "WHERE e.eventName = :eventName AND e.createdAt >= :since")
    long countDistinctSessions(@Param("eventName") String eventName,
                               @Param("since") LocalDateTime since);

    /** Top labels for an event (e.g. most-viewed products) since a cutoff. */
    @Query("SELECT e.label, COUNT(e) AS c FROM AnalyticsEvent e " +
           "WHERE e.eventName = :eventName AND e.label IS NOT NULL AND e.createdAt >= :since " +
           "GROUP BY e.label ORDER BY c DESC")
    List<Object[]> topLabels(@Param("eventName") String eventName,
                             @Param("since") LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}
