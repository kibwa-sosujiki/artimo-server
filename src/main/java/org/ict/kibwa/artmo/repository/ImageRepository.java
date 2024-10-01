package org.ict.kibwa.artmo.repository;

import org.ict.kibwa.artmo.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ImageRepository extends JpaRepository<Image, Long> {
    Image findTopByOrderByCreatedAtDesc();

    Image findTopByCreatedAtLessThanOrderByCreatedAtDesc(LocalDateTime createdAt);
}
