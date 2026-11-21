package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Student;

public interface EarnedAchievementRepository extends JpaRepository<EarnedAchievement, Long> {

  List<EarnedAchievement> findByStudentOrderByEarnedAtDesc(Student student);

  List<EarnedAchievement> findByStudentNumber(Long studentNumber);

  // two ratings at the same time can both award the same badge: the second waits for the first and adds nothing,
  // instead of failing on the unique key and taking its rating down with it. 1 if it was added, 0 if it was there
  @Modifying
  @Query(nativeQuery = true, value = """
      insert into earned_achievement (student_id, achievement, earned_at, verification_code)
      values (:studentNumber, :achievement, :earnedAt, :verificationCode)
      on conflict (student_id, achievement) do nothing""")
  int insertIfNew(Long studentNumber, String achievement, LocalDateTime earnedAt, String verificationCode);

  default int saveIfNew(EarnedAchievement earned) {
    return insertIfNew(earned.getStudent().getNumber(), earned.getAchievement().name(), earned.getEarnedAt(),
        earned.getVerificationCode());
  }

}
