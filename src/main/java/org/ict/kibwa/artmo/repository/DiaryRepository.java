package org.ict.kibwa.artmo.repository;

import org.ict.kibwa.artmo.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiaryRepository extends JpaRepository<Diary, Integer> {
    // 가장 최근에 생성된 Diary를 찾는 메서드
    Optional<Diary> findTopByOrderByCreatedAtDesc();
}