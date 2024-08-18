package org.ict.kibwa.artmo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryDto {
    private int id;
    private List<String> sources ;
    private String thumb;
    private String title;
}

