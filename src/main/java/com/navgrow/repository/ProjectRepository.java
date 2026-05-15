package com.navgrow.repository;
import com.navgrow.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findBySlug(String slug);
    List<Project> findByFeaturedTrueOrderBySortOrderAsc();
    List<Project> findByCategoryOrderBySortOrderAsc(String category);
    List<Project> findAllByOrderBySortOrderAscCreatedAtDesc();
}