package org.ict.kibwa.artmo.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public String upload(MultipartFile multipartFile, String dirName) throws IOException {
        String fileName = createFileName(multipartFile.getOriginalFilename(), dirName);
        File uploadFile = convert(multipartFile);  // MultipartFile을 File로 변환
        String uploadImageUrl = putS3(uploadFile, fileName);  // S3에 업로드
        removeNewFile(uploadFile);  // 로컬에 저장된 임시 파일 삭제

        return uploadImageUrl;
    }

    // 파일 이름을 생성하는 메서드 (UUID 포함)
    private String createFileName(String originalFilename, String dirName) {
        String uuid = UUID.randomUUID().toString();
        return dirName + "/" + uuid + "_" + originalFilename.replaceAll("\\s", "_");
    }

    // MultipartFile을 File로 변환하는 메서드
    private File convert(MultipartFile file) throws IOException {
        File convertFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        if (convertFile.createNewFile()) {
            try (FileOutputStream fos = new FileOutputStream(convertFile)) {
                fos.write(file.getBytes());
            }
            return convertFile;
        }
        throw new IllegalArgumentException("파일 변환에 실패했습니다: " + file.getOriginalFilename());
    }

    // S3에 파일을 업로드하는 메서드
    private String putS3(File uploadFile, String fileName) {
        amazonS3.putObject(new PutObjectRequest(bucket, fileName, uploadFile)
                .withCannedAcl(CannedAccessControlList.PublicRead));
        return amazonS3.getUrl(bucket, fileName).toString();
    }

    // 로컬에 저장된 임시 파일을 삭제하는 메서드
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