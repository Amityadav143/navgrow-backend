package com.navgrow.repository;
import com.navgrow.entity.QuoteRequest;
import com.navgrow.enums.QuoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, UUID> {
    Page<QuoteRequest> findByStatusOrderByCreatedAtDesc(QuoteStatus status, Pageable pageable);
    Page<QuoteRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(QuoteStatus status);
}