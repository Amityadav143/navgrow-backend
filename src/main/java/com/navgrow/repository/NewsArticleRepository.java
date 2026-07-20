/*
 * © 2024–2025 Navgrow Engineering Service Pvt. Ltd. All rights reserved.
 * CIN: U74999WB2022PTC256012 | navgrow.org | info@navgrow.org
 *
 * PROPRIETARY & CONFIDENTIAL — Navgrow Engineering Platform v1.0
 * Unauthorised copying or distribution is strictly prohibited.
 */
package com.navgrow.repository;
import com.navgrow.entity.NewsArticle;
import com.navgrow.enums.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID> {
    Optional<NewsArticle> findBySlug(String slug);
    Page<NewsArticle> findByStatusOrderByPublishedAtDesc(NewsStatus status, Pageable pageable);
    Page<NewsArticle> findByStatusOrderByCreatedAtDesc(NewsStatus status, Pageable pageable);
    Page<NewsArticle> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<NewsArticle> findByCategoryAndStatusOrderByPublishedAtDesc(String category, NewsStatus status, Pageable pageable);
    long countByStatus(NewsStatus status);

    // Text search across title OR excerpt for a given status
    Page<NewsArticle> findByStatusAndTitleContainingIgnoreCaseOrStatusAndExcerptContainingIgnoreCase(
        com.navgrow.enums.NewsStatus s1, String title,
        com.navgrow.enums.NewsStatus s2, String excerpt,
        Pageable pageable);
}