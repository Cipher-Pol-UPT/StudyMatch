package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Student;

public interface EarnedAchievementRepository extends JpaRepository<EarnedAchievement, Long> {

  List<EarnedAchievement> findByStudentOrderByEarnedAtDesc(Student student);

}
