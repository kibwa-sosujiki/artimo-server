package org.ict.kibwa.artmo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Getter
@Setter
@Entity
@NoArgsConstructor
public class Diary implements Serializable {

    @Serial
    private static final long serialVersionUID = 2712140941676560653L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Integer diaryId;

    @NonNull
    @Column(name = "emotion_type", length = 50)
    private String emotionType;

    @NonNull
    @Column(name = "title", length = 200)
    private String title;

    @NonNull
    @Column(name = "contents", length = 300)
    private String contents;

    @Column(name = "imgUrl", length = 300)
    private String imgUrl;

    @Column(name = "vidUrl", length = 300)
    private String vidUrl;

    @NonNull
    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @NonNull
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder
    public Diary(String emotionType, String title, String contents, String imgUrl, String vidUrl) {
        this.emotionType = emotionType;
        this.title = title;
        this.contents = contents;
        this.imgUrl = imgUrl;
        this.vidUrl = vidUrl;
    }
}
