package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.dtos.VerifiedAchievementDTO;
import com.studymatch.dtos.VerifiedCertificateDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AchievementRecord;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Student;
import com.studymatch.repositories.AchievementRecordRepository;

@Service
public class AchievementRecordService {

  private final AchievementRecordRepository achievementRecordRepository;

  public AchievementRecordService(AchievementRecordRepository achievementRecordRepository) {
    this.achievementRecordRepository = achievementRecordRepository;
  }

  // every export with several achievements is a new record, with its own code
  public AchievementRecord create(Student student, List<EarnedAchievement> achievements) {
    return achievementRecordRepository.save(new AchievementRecord(student, achievements));
  }

  public VerifiedCertificateDTO verify(String code) {
    AchievementRecord record = achievementRecordRepository.findByVerificationCode(code)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    return VerifiedCertificateDTO.of(true, record.getStudent(), record.getIssuedAt(),
        record.getAchievements().stream().map(VerifiedAchievementDTO::from).toList());
  }

}
