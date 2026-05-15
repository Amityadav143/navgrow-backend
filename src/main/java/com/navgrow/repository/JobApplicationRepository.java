package com.navgrow.repository;
import com.navgrow.entity.JobApplication;
import com.navgrow.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    Page<JobApplication> findByJobIdOrderByCreatedAtDesc(UUID jobId, Pageable pageable);
    Page<JobApplication> findByStatusOrderByCreatedAtDesc(ApplicationStatus status, Pageable pageable);
    long countByStatus(ApplicationStatus status);
}