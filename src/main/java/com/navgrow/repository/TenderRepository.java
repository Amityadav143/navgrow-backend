package com.navgrow.repository;
import com.navgrow.entity.Tender;
import com.navgrow.enums.TenderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface TenderRepository extends JpaRepository<Tender, UUID> {
    List<Tender> findByStatusOrderByDeadlineAsc(TenderStatus status);
    List<Tender> findByFeaturedTrueAndStatusOrderByDeadlineAsc(TenderStatus status);
}