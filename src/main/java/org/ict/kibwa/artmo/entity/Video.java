package org.ict.kibwa.artmo.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "Video")
@NoArgsConstructor
public class Video implements Serializable {

    @Serial
    private static final long serialVersionUID = 987654321L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long videoId;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    @ManyToOne
    @JoinColumn(name = "img_id", nullable = false)
    private Image image;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Builder
    public Video(String videoUrl, Image image) {
        this.videoUrl = videoUrl;
        this.image = image;
    }
}
