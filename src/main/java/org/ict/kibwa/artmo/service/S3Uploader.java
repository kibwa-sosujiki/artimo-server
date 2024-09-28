package org.ict.kibwa.artmo.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class S3Uploader {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket-name}")
    private String bucket;

    // 이미지 파일을 업로드하는 메서드 (파일 타입에 따라 분기 처리)
    public String upload(MultipartFile multipartFile, String dirName) throws IOException {
        String fileName = createFileName(multipartFile.getOriginalFilename(), dirName);
        String contentType = detectFileType(multipartFile);  // 파일 타입 확인
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);  // 파일 타입 설정
        metadata.setContentLength(multipartFile.getSize());

        // S3에 파일 업로드
        InputStream inputStream = multipartFile.getInputStream();
        amazonS3.putObject(new PutObjectRequest(bucket, fileName, inputStream, metadata));

        return amazonS3.getUrl(bucket, fileName).toString();  // 업로드된 파일 URL 반환
    }

    // InputStream으로 업로드하는 메서드
    public String upload(InputStream inputStream, String fileName, ObjectMetadata metadata) {
        amazonS3.putObject(new PutObjectRequest(bucket, fileName, inputStream, metadata));
        return amazonS3.getUrl(bucket, fileName).toString();
    }


    // 파일 이름을 생성하는 메서드 (UUID 포함)
    private String createFileName(String originalFilename, String dirName) {
        String uuid = UUID.randomUUID().toString();
        return dirName + "/" + uuid + "_" + originalFilename.replaceAll("\\s", "_");
    }

    // 파일의 MIME 타입을 확인하는 메서드
    private String detectFileType(MultipartFile file) throws IOException {
        Tika tika = new Tika();
        String detectedType = tika.detect(file.getInputStream());

        if (detectedType.equals("image/png")) {
            return "image/png";
        } else if (detectedType.equals("image/jpeg")) {
            return "image/jpeg";
        } else {
            throw new IllegalArgumentException("지원되지 않는 파일 형식입니다. PNG 또는 JPEG만 허용됩니다.");
        }
    }

    // 로컬에 저장된 임시 파일을 삭제하는 메서드 (사용하지 않을 경우 생략 가능)
    private void removeNewFile(File targetFile) {
        if (targetFile.delete()) {
            log.info("임시 파일이 삭제되었습니다: " + targetFile.getName());
        } else {
            log.error("임시 파일 삭제에 실패했습니다: " + targetFile.getName());
        }
    }

    // S3에서 파일을 삭제하는 메서드
    public void deleteFile(String fileName) {
        try {
            String decodedFileName = URLDecoder.decode(fileName, "UTF-8");
            log.info("Deleting file from S3: " + decodedFileName);
            amazonS3.deleteObject(bucket, decodedFileName);
        } catch (UnsupportedEncodingException e) {
            log.error("파일 이름 디코딩 중 오류 발생: {}", e.getMessage());
        }
    }

    // 파일을 업데이트하는 메서드 (기존 파일 삭제 후 새 파일 업로드)
    public String updateFile(MultipartFile newFile, String oldFileName, String dirName) throws IOException {
        log.info("기존 파일 삭제: " + oldFileName);
        deleteFile(oldFileName);  // 기존 파일 삭제
        return upload(newFile, dirName);  // 새 파일 업로드
    }
}
