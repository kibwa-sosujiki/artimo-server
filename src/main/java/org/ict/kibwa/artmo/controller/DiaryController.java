package org.ict.kibwa.artmo.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ict.kibwa.artmo.dto.DiaryDto;
import org.ict.kibwa.artmo.entity.Diary;
import org.ict.kibwa.artmo.service.DiaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @GetMapping("")
    public RedirectView index() {
        return new RedirectView("/diary/sample");
    }

    @GetMapping("/sample")
    public DiaryDto findSampleFile(@RequestParam(defaultValue = "") String title, @RequestParam(defaultValue = "") String content) {
        // JSON 형태로 반환
        return DiaryDto.builder()
                .id(1)
                .sources(Collections.singletonList("http://example.com/video.mp4"))
                .thumb("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg")
                .title("By Google")
                .build();
    }

    @GetMapping("/list")
    public List<DiaryDto> findAllHistory() {

        // TODO: 그동안 생성된 모든 Diary 정보 리턴하기

        List<Diary> diaryList = diaryService.getAll();

        // 모든 다이어리 조회 결과를 diaryDtoList로 변환 후 JSON 형태로 반환
        return diaryList.stream().map(diary -> DiaryDto.builder()
                .id(diary.getDiaryId())
                 //.sources(Collections.singletonList(diary.getVidUrl()))
                 //.thumb(diary.getImgUrl())
                .title(diary.getTitle())
                .build()).toList();
    }

    @GetMapping("/list/last")
    public DiaryDto findLast() {

        // TODO: 가장 마지막에 생성된 Diary 리턴하기

        Diary diary = diaryService.findLast().orElseThrow(null);
        if (diary == null) {
            throw new NoSuchElementException("No diary found");
        }

        return DiaryDto.builder()
                .id(diary.getDiaryId())
                 //.sources(Collections.singletonList(diary.getVidUrl()))
                 //.thumb(diary.getImgUrl())
                .title(diary.getTitle())
                .build();
    }

    /**
     * Diary 작성
     */
    @PostMapping("/create")
    public ResponseEntity<Diary> createDiary(@RequestBody Diary diary) {
        Diary createdDiary = diaryService.save(diary);
        return ResponseEntity.ok(createdDiary);
    }
}
