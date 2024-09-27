package org.ict.kibwa.artmo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.kibwa.artmo.dto.DiaryDto;
import org.ict.kibwa.artmo.entity.Diary;
import org.ict.kibwa.artmo.service.DiaryService;
import org.ict.kibwa.artmo.service.S3Uploader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
@Slf4j
public class DiaryController {

    private final DiaryService diaryService;
    private final S3Uploader s3Uploader;

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

    @GetMapping("/test")
    public String test(){
        return "test";
    }

    @ApiOperation(value = "일기 작성")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Diary> createDiary(
            @RequestPart("diary") String diaryData, // JSON 문자열로 일기 데이터를 받음
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {

        try {
            // JSON 문자열을 Diary 객체로 변환
            ObjectMapper objectMapper = new ObjectMapper();
            Diary diary = objectMapper.readValue(diaryData, Diary.class);

            // 이미지 파일이 있으면 S3에 업로드 후 이미지 URL을 Diary 객체에 설정
            if (imageFile != null && !imageFile.isEmpty()) {
                String imageUrl = s3Uploader.upload(imageFile, "diary-images");  // S3에 이미지 업로드
                diary.setDimgUrl(imageUrl);  // 이미지 URL을 Diary 객체에 설정
            }

            // Diary 저장
            Diary createdDiary = diaryService.save(diary);
            return ResponseEntity.ok(createdDiary);  // 작성된 Diary 객체를 반환

        } catch (IOException e) {
            log.error("Diary 작성 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }


}

