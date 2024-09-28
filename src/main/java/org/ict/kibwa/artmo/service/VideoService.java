package org.ict.kibwa.artmo.service;

import lombok.RequiredArgsConstructor;
import org.ict.kibwa.artmo.entity.Video;
import org.ict.kibwa.artmo.repository.VideoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;

    public List<Video> getAllVideos(){
        return videoRepository.findAll();
    }

    public Video getLatestVideo(){
        return videoRepository.findTopByOrderByCreatedAtDesc();
    }
}
