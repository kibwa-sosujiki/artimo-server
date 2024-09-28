package org.ict.kibwa.artmo.controller;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Getter;
import org.ict.kibwa.artmo.dto.ImageDTO;
import org.ict.kibwa.artmo.dto.VideoDTO;
import org.ict.kibwa.artmo.entity.Image;
import org.ict.kibwa.artmo.entity.Video;
import org.ict.kibwa.artmo.service.ImageService;
import org.ict.kibwa.artmo.service.VideoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ict.kibwa.artmo.dto.DiaryDto;
import org.ict.kibwa.artmo.entity.Diary;
import org.ict.kibwa.artmo.service.DiaryService;
import org.ict.kibwa.artmo.service.S3Uploader;

import org.springframework.beans.factory.annotation.Value;
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
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/diary")
@RequiredArgsConstructor
@Slf4j
public class DiaryController {

    private final DiaryService diaryService;
    private final ImageService imageService;
    private final VideoService videoService;
    private final S3Uploader s3Uploader;
    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();  // 폴링 스케줄러 추가
    private ScheduledFuture<?> scheduledFuture;

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucket;

    private static final Logger logger = LoggerFactory.getLogger(DiaryController.class);

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

    @GetMapping("/list")
    public ResponseEntity<List<DiaryDto>> getDiaryList() {
        List<Diary> diaryList = diaryService.getAll();
        List<DiaryDto> diaryDtoList = diaryList.stream()
                .map(this::convertToDto)  // 변환 메서드 호출
                .collect(Collectors.toList());
        return ResponseEntity.ok(diaryDtoList);
    }

    private DiaryDto convertToDto(Diary diary) {
        List<ImageDTO> imageDTOs = diary.getImages().stream()
                .map(img -> ImageDTO.builder()
                        .imgId(img.getImgId())
                        .imgUrl(img.getImgUrl())
                        .build())
                .collect(Collectors.toList());

        return DiaryDto.builder()
                .diaryId(diary.getDiaryId())
                .emotionType(diary.getEmotionType())
                .title(diary.getTitle())
                .contents(diary.getContents())
                .caption(diary.getCaption())
                .dimgUrl(diary.getDimgUrl())
                .images(imageDTOs)
                .createdAt(diary.getCreatedAt())
                .updatedAt(diary.getUpdatedAt())
                .build();
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
        headers.set("Authorization", "Bearer openai-key");
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
                + "Ensure the artwork is high-quality, visually appealing, and evokes a sense of peace and comfort. never including text.";

        return finalResponse;  // 변환된 최종 결과를 반환
    }


    /**
     * 텍스트 분석 및 감정 추출 후 이미지 생성 API
     */
    @PostMapping("/emotion-image/{diaryId}")
    public ResponseEntity<Map<String, String>> analyzeEmotionAndGenerateImage(
            @PathVariable("diaryId") Long diaryId,
            @RequestBody Map<String, String> requestBody) throws IOException {

        String contents = requestBody.get("contents");

        // GPT API를 사용하여 텍스트에서 감정 분석을 수행 후 이미지 프롬프트로 바꿈
        String gptResponse = getEmotionAnalysisFromGPT(contents);

        // DALL-E 3 이미지 생성 요청
        String imageUrl = generateImageFromDalle(gptResponse);

        // 생성된 이미지를 S3에 업로드
        String s3ImageUrl = uploadImageFromUrlToS3(imageUrl, "emotion-images");

        // 일기 조회
        Diary diary = diaryService.findById(diaryId).orElseThrow(()-> new RuntimeException("Diary not found"));

        // Image 엔티티 생성 후 S3 URL 저장
        Image image = new Image();
        image.setImgUrl(s3ImageUrl);
        image.setDiary(diary);

        Image savedImage = imageService.save(image);

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("gptResponse", gptResponse); // GPT 응답 추가
        response.put("imageUrl", s3ImageUrl);  // 생성된 이미지 URL 반환
        response.put("imageId", String.valueOf(savedImage.getImgId()));  // 저장된 이미지 ID 반환

        return ResponseEntity.ok(response);
    }

    // 이미지 URL을 받아 바로 S3로 PNG 형식으로 변환하여 업로드하는 함수
    private String uploadImageFromUrlToS3(String imageUrl, String s3Path) throws IOException {
        log.info("Starting upload for image URL: {}", imageUrl);  // 이미지 URL 로그 추가

        try (InputStream inputStream = new URL(imageUrl).openStream()) {
            // 이미지 다운로드 및 BufferedImage로 변환
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IOException("Failed to download image from URL: " + imageUrl);
            }

            // BufferedImage를 PNG로 변환
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(originalImage, "png", os);
            byte[] pngData = os.toByteArray();

            // 변환된 PNG 이미지를 S3에 업로드
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(pngData.length);
            metadata.setContentType("image/png");

            // 파일 이름 생성 (경로 포함)
            String fileName = createFileNameFromUrl(imageUrl, s3Path, "png");
            log.info("Uploading to S3 with file name: {}", fileName);

            // ByteArrayInputStream으로 S3에 업로드
            try (InputStream byteInputStream = new ByteArrayInputStream(pngData)) {
                amazonS3.putObject(new PutObjectRequest(bucket, fileName, byteInputStream, metadata));
            }

            // 업로드된 파일의 S3 URL 반환
            String s3ImageUrl = amazonS3.getUrl(bucket, fileName).toString();
            log.info("File successfully uploaded to S3 at URL: {}", s3ImageUrl);

            return s3ImageUrl;
        }
    }

    // 파일 이름을 생성할 때 확장자를 포함하여 생성하는 메서드
    private String createFileNameFromUrl(String imageUrl, String s3Path, String extension) {
        String uuid = UUID.randomUUID().toString();
        return s3Path + "/" + uuid + "." + extension;
    }

    // 파일의 Content-Type을 감지하는 메서드
    private String detectContentType(String imageUrl) {
        if (imageUrl.endsWith(".png")) {
            return "image/png";
        } else if (imageUrl.endsWith(".jpg") || imageUrl.endsWith(".jpeg")) {
            return "image/jpeg";
        } else {
            return "application/octet-stream";  // 알 수 없는 파일 형식의 경우 기본 값
        }
    }

    // 이미지 URL에서 파일 이름 생성하는 메서드
    private String createFileNameFromUrl(String imageUrl, String s3Path) {
        String uuid = UUID.randomUUID().toString();
        String fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
        return s3Path + "/" + uuid + "_" + fileName.replaceAll("\\s", "_");
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
        headers.set("Authorization", "Bearer openai-key");
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
    @PostMapping("/text-to-video/{diaryId}")
    public ResponseEntity<Map<String, String>> analyzeEmotionAndGenerateImageAndVideo(
            @PathVariable("diaryId") Long diaryId) throws IOException {

        // Step 1: 일기 조회 및 컨텐츠 추출
        Diary diary = diaryService.findById(diaryId).orElseThrow(() -> new RuntimeException("Diary not found"));
        String contents = diary.getContents();

        // Step 2: 텍스트 분석 및 감정 추출
        String gptResponse = getEmotionAnalysisFromGPT(contents);

        // Step 3: DALL-E 3 이미지 생성 요청
        String imageUrl = generateImageFromDalle(gptResponse);

        // Step 4: 생성된 이미지를 S3에 업로드
        String s3ImageUrl = uploadImageFromUrlToS3(imageUrl, "emotion-images");

        // Step 5: 이미지 DB 저장
        Image image = new Image();
        image.setImgUrl(s3ImageUrl);
        image.setDiary(diary);
        Image savedImage = imageService.save(image);

        // Step 6: 이미지에서 비디오 생성 요청
        String videoId = startImageToVideoGeneration(s3ImageUrl);

        // Step 7: 비디오 생성 완료 대기
        byte[] videoData = pollForVideoResult(videoId);

        // Step 8: 비디오 S3에 업로드
        InputStream videoInputStream = new ByteArrayInputStream(videoData);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(videoData.length);
        metadata.setContentType("video/mp4");
        String s3VideoUrl = s3Uploader.upload(videoInputStream, "emotion-videos/" + savedImage.getImgId() + ".mp4", metadata);

        // Step 9: 비디오 DB 저장
        Video video = new Video();
        video.setImage(savedImage);
        video.setVideoUrl(s3VideoUrl);
        videoService.save(video);

        // 응답 생성
        Map<String, String> response = new HashMap<>();
        response.put("gptResponse", gptResponse); // GPT 응답 추가
        response.put("imageUrl", s3ImageUrl);     // 생성된 이미지 URL 반환
        response.put("videoUrl", s3VideoUrl);     // 생성된 비디오 URL 반환

        return ResponseEntity.ok(response);
    }



    private byte[] pollForVideoResult(String videoId) {
        String apiUrl = "https://api.stability.ai/v2beta/image-to-video/result/" + videoId;
        String apiKey = "STA-KEY";

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
        String apiKey = "STA-KEY";

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
        String apiKey = "STA-KEY";

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

    @Operation(summary = "전체 이미지와 동영상 조회")
    @GetMapping("all")
    public ResponseEntity<Map<String, Object>> getAllMedia(){
        Map<String, Object> response = new HashMap<>();

        // 모든 이미지와 동영상을 통합 리스트로 반환
        List<Map<String, Object>> mediaList = new ArrayList<>();

        // 모든 이미지 불러오기
        List<Image> images = imageService.getAllImages();
        images.forEach(image -> {
            Map<String, Object> media = new HashMap<>();
            media.put("id", image.getImgId());  // 이미지 ID
            media.put("thumb", image.getImgUrl());  // 썸네일 이미지 URL

            // 해당 이미지와 관련된 동영상 추가
            List<String> videoUrls = image.getVideos().stream()
                    .map(Video::getVideoUrl)
                    .collect(Collectors.toList());
            media.put("sources", videoUrls);  // 동영상 URL 리스트

            mediaList.add(media);
        });

        response.put("result", mediaList);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "가장 최신 이미지와 동영상 조회")
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestMedia() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> result = new HashMap<>();

        // 최신 이미지 가져오기
        Image latestImage = imageService.getLatestImage();
        if (latestImage != null) {
            result.put("id", latestImage.getImgId());
            result.put("thumb", latestImage.getImgUrl());
        } else {
            result.put("id", null);
            result.put("thumb", "No image available");
        }

        // 최신 동영상 가져오기
        Video latestVideo = videoService.getLatestVideo();
        if (latestVideo != null) {
            result.put("sources", latestVideo.getVideoUrl());
        } else {
            result.put("sources", "No video available");
        }

        response.put("result", result);
        return ResponseEntity.ok(response);
    }

}