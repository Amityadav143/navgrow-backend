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
    Page<NewsArticle> findByCategoryAndStatusOrderByPublishedAtDesc(String category, NewsStatus status, Pageable pageable);
    long countByStatus(NewsStatus status);
}