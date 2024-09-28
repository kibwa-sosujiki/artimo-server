package org.ict.kibwa.artmo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.kibwa.artmo.dto.DiaryDto;
import org.ict.kibwa.artmo.entity.Diary;
import org.ict.kibwa.artmo.service.DiaryService;
import org.ict.kibwa.artmo.service.S3Uploader;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
@Slf4j
public class DiaryController {

    private final DiaryService diaryService;
    private final S3Uploader s3Uploader;
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();  // 폴링 스케줄러 추가
    private ScheduledFuture<?> scheduledFuture;

    private static final Logger logger = LoggerFactory.getLogger(DiaryController.class);

    @GetMapping("/list")
    public List<DiaryDto> findAllHistory() {

        // TODO: 그동안 생성된 모든 Diary 정보 리턴하기

        List<Diary> diaryList = diaryService.getAll();

        // 모든 다이어리 조회 결과를 diaryDtoList로 변환 후 JSON 형태로 반환
        return diaryList.stream().map(diary -> DiaryDto.builder()
                .id(diary.getDiaryId())
                .title(diary.getTitle())
                .build()).toList();
    }

    @Operation(summary = "일기 작성")
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


    /**
     * 텍스트 분석 및 감정 추출 API
     */
    @PostMapping("/emotion")
    public ResponseEntity<String> analyzeEmotion(@RequestBody Map<String, String> requestBody) {
        String contents = requestBody.get("contents");

        // GPT API를 사용하여 텍스트에서 감정 분석을 수행
        String gptResponse = getEmotionAnalysisFromGPT(contents);

        // GPT 응답을 그대로 반환
        return ResponseEntity.ok(gptResponse);
    }

    /**
     * GPT API를 호출하여 감정 분석 수행 후 이미지 생성 프롬프트로 바꿔서 출력
     */
    private String getEmotionAnalysisFromGPT(String contents) {
        String gptApiUrl = "https://api.openai.com/v1/chat/completions";  // GPT Chat 모델 엔드포인트

        RestTemplate restTemplate = new RestTemplate();

        // GPT-4 대화 모델 형식에 맞게 요청 본문 구성
        Map<String, Object> request = new HashMap<>();
        request.put("model", "gpt-4");  // GPT-4 모델 사용
        request.put("messages", List.of(
                Map.of("role", "system", "content",
                        "You are a helpful assistant that analyzes emotions from text."),
                Map.of("role", "user", "content",
                        "Based on the diary contents provided, pick the strongest emotion and explain it in one sentence, including the reason for that emotion. "
                                + "Ensure the emotion is described in a specific and concrete way, and keep the explanation concise: "
                                + contents)
        ));
        request.put("max_tokens", 100);

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer GPT-KEY");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // 요청 엔티티 생성
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        // GPT API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(gptApiUrl, entity, Map.class);

        // GPT로부터 받은 응답에서 choices 내의 content 부분 추출
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String gptContent = (String) message.get("content");

        // 결과를 새로운 형식으로 변환하여 반환
        String finalResponse = "Generate an image based on the user's emotions: ( "
                + gptContent
                + " ). Use Yellow for positive emotions, Green for mental rest, and Blue for sadness. "
                + "The image should depict a beautiful, European-style landscape or nature scene with a soft oil painting texture or a realistic feel. "
                + "Ensure the artwork is high-quality, visually appealing, and evokes a sense of peace and comfort.";

        return finalResponse;  // 변환된 최종 결과를 반환
    }


    /**
     * 텍스트 분석 및 감정 추출 후 이미지 생성 API
     */
    @PostMapping("/emotion-image")
    public ResponseEntity<Map<String, String>> analyzeEmotionAndGenerateImage(@RequestBody Map<String, String> requestBody) {
        String contents = requestBody.get("contents");

        // GPT API를 사용하여 텍스트에서 감정 분석을 수행 후 이미지 프롬프트로 바꿈
        String gptResponse = getEmotionAnalysisFromGPT(contents);

        // DALL-E 3 이미지 생성 요청
        String imageUrl = generateImageFromDalle(gptResponse);

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("gptResponse", gptResponse); // GPT 응답 추가
        response.put("imageUrl", imageUrl);  // 생성된 이미지 URL 반환

        return ResponseEntity.ok(response);
    }

    /**
     * DALL-E 3 API를 호출하여 이미지 생성
     */
    private String generateImageFromDalle(String prompt) {
        String dalleApiUrl = "https://api.openai.com/v1/images/generations";

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> request = new HashMap<>();
        request.put("prompt", prompt);
        request.put("n", 1);  // 하나의 이미지 생성
        request.put("size", "1024x1024");

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer DALLE-KEY");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // 요청 엔티티 생성
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        // DALL-E 3 API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(dalleApiUrl, entity, Map.class);

        // 이미지 URL 추출
        List<Map<String, String>> data = (List<Map<String, String>>) response.getBody().get("data");
        return data.get(0).get("url");  // 첫 번째 이미지 URL 반환
    }

    /**
     * 텍스트 분석 후 이미지 생성, 그리고 이미지에서 비디오 생성 API
     */
    @PostMapping("/text-to-video")
    public ResponseEntity<byte[]> analyzeEmotionAndGenerateImageAndVideo(@RequestBody Map<String, String> requestBody) throws IOException {
        String contents = requestBody.get("contents");

        // Step 1: 텍스트 분석 및 감정 추출
        String gptResponse = getEmotionAnalysisFromGPT(contents);

        // Step 3: DALL-E 3 이미지 생성 요청
        String imageUrl = generateImageFromDalle(gptResponse);

        // Step 4: 이미지에서 비디오 생성 요청
        String videoId = startImageToVideoGeneration(imageUrl);

        logger.debug("contents: {}", contents);
        logger.debug("gptResponse: {}", gptResponse);
        logger.debug("Generated Image URL: {}", imageUrl);

        // Step 5: 폴링을 통해 비디오 생성 완료될 때까지 대기
        byte[] videoData = pollForVideoResult(videoId);

        // Step 6: 최종 비디오 파일 반환
        return ResponseEntity.ok()
                .header("Content-Type", "video/mp4")
                .body(videoData);

    }

    private byte[] pollForVideoResult(String videoId) {
        String apiUrl = "https://api.stability.ai/v2beta/image-to-video/result/" + videoId;
        String apiKey = "STABILITY-KEY";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Accept", "video/*");

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        while (true) {
            ResponseEntity<byte[]> response = restTemplate.exchange(apiUrl, HttpMethod.GET, requestEntity, byte[].class);

            if (response.getStatusCode() == HttpStatus.OK) {
                // 비디오가 완료되었을 때 비디오 파일을 반환
                return response.getBody();
            } else if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                // 비디오 생성 중이므로 일정 시간 대기
                try {
                    Thread.sleep(2000);  // 2초 대기 후 다시 요청
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                throw new RuntimeException("Error fetching video result: " + response.getStatusCode());
            }
        }
    }


    /**
     * Stability AI의 Image-to-Video API를 호출하여 동영상 생성을 시작
     */
    private String startImageToVideoGeneration(String imageUrl) throws IOException {
        String apiUrl = "https://api.stability.ai/v2beta/image-to-video";
        String apiKey = "STABILITY-KEY";

        // 1. 이미지 다운로드 및 해상도 조정 (768x768으로 변경)
        String resizedImagePath = downloadAndResizeImage(imageUrl, "resizedImage.png", 768, 768);

        // 2. RestTemplate으로 multipart/form-data 요청 구성
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new FileSystemResource(resizedImagePath));  // 조정된 파일을 전송
        body.add("seed", "0");  // seed는 0으로 설정
        body.add("cfg_scale", "1.8");  // cfg_scale 기본 값
        body.add("motion_bucket_id", "127");  // motion_bucket_id 기본 값

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Stability AI API 호출
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestEntity, Map.class);

        // 응답에서 비디오 생성 ID 추출
        Map<String, String> responseBody = (Map<String, String>) response.getBody();
        return responseBody.get("id");
    }

    private String downloadAndResizeImage(String imageUrl, String outputFilePath, int targetWidth, int targetHeight) throws IOException {
        // 1. 이미지 다운로드
        BufferedImage originalImage;
        try (InputStream in = new URL(imageUrl).openStream()) {
            originalImage = ImageIO.read(in);
        }

        // 2. 해상도 변경
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        // 3. 파일로 저장
        File outputFile = new File(outputFilePath);
        ImageIO.write(resizedImage, "png", outputFile);

        return outputFilePath;
    }

    /**
     * 비디오 생성 결과 확인 API - 폴링 함수
     */
    @GetMapping("/image-to-video/result/{id}")
    public ResponseEntity<String> getVideoGenerationResult(@PathVariable("id") String videoId) {
        String apiUrl = "https://api.stability.ai/v2beta/image-to-video/result/" + videoId;
        String apiKey = "STABILITY-KEY";  // Stability API Key

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("accept", "video/*");  // 비디오 파일 직접 반환

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        // Stability AI API 호출하여 비디오 생성 결과 확인
        ResponseEntity<byte[]> response = restTemplate.exchange(apiUrl, HttpMethod.GET, requestEntity, byte[].class);

        // 비디오가 아직 생성 중이라면 상태 202
        if (response.getStatusCode().is2xxSuccessful()) {
            // 비디오 생성이 완료된 경우
            stopPolling();  // 폴링 종료
            return ResponseEntity.ok()
                    .header("Content-Type", "video/mp4")
                    .body(new String(response.getBody()));  // 비디오 데이터를 직접 반환
        } else if (response.getStatusCodeValue() == 202) {
            // 아직 생성 중인 경우
            return ResponseEntity.status(202).body("Video is still in progress.");
        } else {
            // 다른 오류 발생
            return ResponseEntity.status(500).body("Error fetching video result.");
        }
    }

    /**
     * 폴링 종료
     */
    private void stopPolling() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }
}