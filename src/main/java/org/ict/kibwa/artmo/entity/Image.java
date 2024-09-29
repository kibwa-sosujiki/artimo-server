package org.ict.kibwa.artmo.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "Image")
@NoArgsConstructor
public class Image implements Serializable {

    @Serial
    private static final long serialVersionUID = 123456789L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "img_id")
    private Long imgId;

    @Column(name = "img_url", length = 1000)
    private String imgUrl;

    @ManyToOne
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @OneToMany(mappedBy = "image", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Video> videos;

    @Column(name = "created_at")
    private LocalDate createdAt;

    @Builder
    public Image(String imgUrl, Diary diary) {
        this.imgUrl = imgUrl;
        this.diary = diary;
    }
}

