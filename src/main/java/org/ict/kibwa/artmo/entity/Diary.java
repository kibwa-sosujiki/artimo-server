package org.ict.kibwa.artmo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "Diary")
@NoArgsConstructor
public class Diary implements Serializable {

    @Serial
    private static final long serialVersionUID = 2712140941676560653L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long diaryId;

    @Column(name = "emotion_type", length = 50)
    private String emotionType;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "contents", length = 300)
    private String contents;

    @Column(name = "caption", length = 300)
    private String caption;

    @Column(name = "dimg_url", length = 300)
    private String dimgUrl;

    @NonNull
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @NonNull
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "diary", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Image> images;

    @Builder
    public Diary(String emotionType, String title, String contents, String caption, String dimgUrl) {
        this.emotionType = emotionType;
        this.title = title;
        this.contents = contents;
        this.caption = caption;
        this.dimgUrl = dimgUrl;
    }
}
