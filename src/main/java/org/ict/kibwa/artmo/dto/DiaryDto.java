package org.ict.kibwa.artmo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryDto {
    private Long diaryId;
    private String emotionType;
    private String title;
    private String contents;
    private String caption;
    private String dimgUrl;
    private List<ImageDTO> images;
    private LocalDate createdAt;
    private LocalDate updatedAt;

}

