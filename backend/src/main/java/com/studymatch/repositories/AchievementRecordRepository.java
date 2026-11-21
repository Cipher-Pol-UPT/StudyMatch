package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.AchievementRecord;

public interface AchievementRecordRepository extends JpaRepository<AchievementRecord, Long> {

  Optional<AchievementRecord> findByVerificationCode(String verificationCode);

}
