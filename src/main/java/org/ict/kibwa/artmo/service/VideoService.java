package org.ict.kibwa.artmo.service;

import lombok.RequiredArgsConstructor;
import org.ict.kibwa.artmo.entity.Image;
import org.ict.kibwa.artmo.entity.Video;
import org.ict.kibwa.artmo.repository.ImageRepository;
import org.ict.kibwa.artmo.repository.VideoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final ImageRepository imageRepository;

    public List<Video> getAllVideos(){
        return videoRepository.findAll();
    }

    public Video getLatestVideo(){
        return videoRepository.findTopByOrderByCreatedAtDesc();
    }

    public Video save(Video video) {
        return videoRepository.save(video);  // Video 엔티티 저장
    }

    public Video getVideoByImageId(Long imageId) {
        return videoRepository.findByImageId(imageId);
    }

    public void setImgId(Long videoId, Long imgId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video not found"));

        Image image = imageRepository.findById(imgId)
                .orElseThrow(() -> new RuntimeException("Image not found"));

        video.setImage(image);  // Image 객체 설정
        videoRepository.save(video);
    }

}
