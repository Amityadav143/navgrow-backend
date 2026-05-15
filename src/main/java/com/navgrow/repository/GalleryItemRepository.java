package com.navgrow.repository;
import com.navgrow.entity.GalleryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface GalleryItemRepository extends JpaRepository<GalleryItem, UUID> {
    List<GalleryItem> findByActiveTrueOrderBySortOrderAscCreatedAtDesc();
    List<GalleryItem> findByCategoryAndActiveTrueOrderBySortOrderAsc(String category);
}