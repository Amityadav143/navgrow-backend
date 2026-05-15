package com.navgrow.repository;
import com.navgrow.entity.JobListing;
import com.navgrow.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobListingRepository extends JpaRepository<JobListing, UUID> {
    List<JobListing> findByStatusOrderByCreatedAtDesc(JobStatus status);
    long countByStatus(JobStatus status);
}