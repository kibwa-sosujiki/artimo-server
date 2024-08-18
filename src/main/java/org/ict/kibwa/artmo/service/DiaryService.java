package org.ict.kibwa.artmo.service;
import lombok.RequiredArgsConstructor;
import org.ict.kibwa.artmo.entity.Diary;
import org.ict.kibwa.artmo.repository.DiaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DiaryService {

    private final DiaryRepository diaryRepository;

    // Retrieve a diary by ID
    @Transactional(readOnly = true)
    public Optional<Diary> findById(Integer id) {
        return diaryRepository.findById(id);
    }

    // Retrieve all diaries
    @Transactional(readOnly = true)
    public List<Diary> getAll() {
        return diaryRepository.findAll();
    }

    // Retrieve the most recently created diary
    @Transactional(readOnly = true)
    public Optional<Diary> findLast() {
        return diaryRepository.findTopByOrderByCreatedAtDesc();
    }

    // Save or update a diary
    @Transactional
    public Diary save(Diary diary) {
        return diaryRepository.save(diary);
    }

    // Delete a diary by ID
    @Transactional
    public void deleteById(Integer id) {
        diaryRepository.deleteById(id);
    }
}

