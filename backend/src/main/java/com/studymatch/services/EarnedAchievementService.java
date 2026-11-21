package com.studymatch.services;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.dtos.ExportCertificateRequestDTO;
import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.dtos.VerifiedAchievementDTO;
import com.studymatch.dtos.VerifiedCertificateDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.enums.Achievement.TutorRecord;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AchievementRecord;
import com.studymatch.models.CertificateEvidence;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Student;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.utils.CertificatePdfWriter;
import com.studymatch.utils.CertificatePdfWriter.Entry;
import com.studymatch.utils.CertificatePdfWriter.Holder;

@Service
public class EarnedAchievementService {

  private final EarnedAchievementRepository earnedAchievementRepository;
  private final StudyRoomService studyRoomService;
  private final ParticipationService participationService;
  private final TutorRoleService tutorRoleService;
  private final AchievementRecordService achievementRecordService;
  private final CertificatePdfWriter certificatePdfWriter;

  public EarnedAchievementService(EarnedAchievementRepository earnedAchievementRepository,
      StudyRoomService studyRoomService, ParticipationService participationService,
      TutorRoleService tutorRoleService, AchievementRecordService achievementRecordService,
      CertificatePdfWriter certificatePdfWriter) {
    this.earnedAchievementRepository = earnedAchievementRepository;
    this.studyRoomService = studyRoomService;
    this.participationService = participationService;
    this.tutorRoleService = tutorRoleService;
    this.achievementRecordService = achievementRecordService;
    this.certificatePdfWriter = certificatePdfWriter;
  }

  public List<AchievementBadgeDTO> getAchievements(Student student) {
    return earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student).stream()
        .map(AchievementBadgeDTO::from)
        .toList();
  }

  // one achievement is a certificate, several are a record. The figures are saved the first time each one is exported
  @Transactional
  public byte[] exportCertificate(Student student, ExportCertificateRequestDTO request) {
    Map<Achievement, EarnedAchievement> earned = earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student)
        .stream()
        .collect(Collectors.toMap(EarnedAchievement::getAchievement, Function.identity()));
    List<EarnedAchievement> chosen = request.achievements().stream()
        .map(achievement -> Optional.ofNullable(earned.get(achievement)).orElseThrow(
            () -> new StudyMatchException(ErrorCode.ACHIEVEMENT_NOT_EARNED, Map.<String, Object>of("achievement", achievement))))
        .sorted(Comparator.comparing(EarnedAchievement::getEarnedAt))
        .toList();

    CertificateEvidence evidence = currentEvidence(student);
    chosen.forEach(achievement -> achievement.recordEvidence(evidence));
    earnedAchievementRepository.saveAll(chosen);

    Holder holder = new Holder(student.getName(), student.getNumber(),
        student.getDegree() == null ? null : student.getDegree().getName());
    Locale locale = Locale.forLanguageTag("pt".equals(request.language()) ? "pt-PT" : "en-GB");
    if (chosen.size() == 1) {
      EarnedAchievement only = chosen.get(0);
      // the first export's date, the one the verify page shows, so a later export prints the same paper
      return certificatePdfWriter.writeCertificate(holder, toEntry(only), only.getVerificationCode(),
          only.getEvidence().recordedAt(), locale);
    }
    AchievementRecord record = achievementRecordService.create(student, chosen);
    return certificatePdfWriter.writeRecord(holder, chosen.stream().map(EarnedAchievementService::toEntry).toList(),
        record.getVerificationCode(), record.getIssuedAt(), locale);
  }

  // public: a single certificate's code, or else a record's
  @Transactional(readOnly = true)
  public VerifiedCertificateDTO verifyCertificate(String code) {
    return earnedAchievementRepository.findByVerificationCode(code)
        .map(earned -> VerifiedCertificateDTO.of(false, earned.getStudent(),
            earned.getEvidence() == null ? null : earned.getEvidence().recordedAt(), List.of(VerifiedAchievementDTO.from(earned))))
        .orElseGet(() -> achievementRecordService.verify(code));
  }

  private CertificateEvidence currentEvidence(Student student) {
    Optional<TutorBadgeDTO> best = tutorRoleService.getBestRatedSubject(student);
    return new CertificateEvidence(studyRoomService.countCounted(student.getNumber()),
        participationService.countStudentsTutored(student.getNumber()), best.map(TutorBadgeDTO::averageRating).orElse(null),
        best.map(TutorBadgeDTO::subject).orElse(null), LocalDate.now());
  }

  private static Entry toEntry(EarnedAchievement earned) {
    return new Entry(earned.getAchievement(), earned.getEarnedAt().toLocalDate(), earned.getEvidence());
  }

  // runs inside the confirm or rating transaction. Achievements are only added, never taken away
  @EventListener
  public void awardAchievements(TutorActivityEvent event) {
    Student tutor = event.tutor();
    TutorRecord record = new TutorRecord(studyRoomService.countCounted(tutor.getNumber()),
        participationService.countStudentsTutored(tutor.getNumber()), tutorRoleService.getSubjectRatings(tutor));
    Set<Achievement> earned = earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(tutor).stream()
        .map(EarnedAchievement::getAchievement)
        .collect(Collectors.toSet());
    Arrays.stream(Achievement.values())
        .filter(achievement -> !earned.contains(achievement) && achievement.isMetBy(record))
        .forEach(achievement -> earnedAchievementRepository.saveIfNew(new EarnedAchievement(tutor, achievement)));
    // telling the tutor about a new badge comes with notifications (#144)
  }

}
