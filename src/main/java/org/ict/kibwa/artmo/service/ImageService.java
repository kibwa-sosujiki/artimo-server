package org.ict.kibwa.artmo.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.ict.kibwa.artmo.entity.Image;
import org.ict.kibwa.artmo.repository.ImageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;

    @Transactional
    public Image save(Image image) {
        return imageRepository.save(image);  // 이미지 저장
    }

    public List<Image> getAllImages(){
        return imageRepository.findAll();
    }

    public Image getLatestImage(){
        return imageRepository.findTopByOrderByCreatedAtDesc();
    }

    // 이전 이미지 가져오기
    public Image getPreviousImage(Image currentImage) {
        if (currentImage == null) {
            return null;
        }
        // 현재 이미지의 createdAt보다 작은 값을 가진 가장 최신 이미지를 가져옴
        return imageRepository.findTopByCreatedAtLessThanOrderByCreatedAtDesc(currentImage.getCreatedAt());
    }

    public Image findById(Long id) {
        return imageRepository.findById(id).orElse(null);  // 이미지 조회
    }

}
