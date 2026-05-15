package com.navgrow.repository;
import com.navgrow.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {
    List<ProductReview> findByProductIdAndApprovedTrueOrderByCreatedAtDesc(UUID productId);
    List<ProductReview> findByApprovedFalseOrderByCreatedAtDesc();

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId AND r.approved = true")
    Double avgRatingForProduct(@Param("productId") UUID productId);
}
