package org.ict.kibwa.artmo.repository;

import org.ict.kibwa.artmo.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<Video, Long> {
    Video findTopByOrderByCreatedAtDesc();
}
