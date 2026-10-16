package com.studymatch.services;

import java.util.List;
import java.util.OptionalDouble;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.dtos.PrivateProfileDTO;
import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Rating;
import com.studymatch.models.Student;
import com.studymatch.models.TutorRole;
import com.studymatch.models.User;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.RatingRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TutorRoleRepository;

@Service
public class StudentService {

  private final StudentRepository studentRepository;
  private final EnrolmentRepository enrolmentRepository;
  private final LessonRepository lessonRepository;
  private final AbsenceRepository absenceRepository;
  private final TutorRoleRepository tutorRoleRepository;
  private final RatingRepository ratingRepository;
  private final EarnedAchievementRepository earnedAchievementRepository;

  public StudentService(StudentRepository studentRepository, EnrolmentRepository enrolmentRepository,
      LessonRepository lessonRepository, AbsenceRepository absenceRepository,
      TutorRoleRepository tutorRoleRepository, RatingRepository ratingRepository,
      EarnedAchievementRepository earnedAchievementRepository) {
    this.studentRepository = studentRepository;
    this.enrolmentRepository = enrolmentRepository;
    this.lessonRepository = lessonRepository;
    this.absenceRepository = absenceRepository;
    this.tutorRoleRepository = tutorRoleRepository;
    this.ratingRepository = ratingRepository;
    this.earnedAchievementRepository = earnedAchievementRepository;
  }

  @Transactional(readOnly = true)
  public StudentProfileDTO getProfile(Long studentNumber, User viewer) {
    Student student = getByNumber(studentNumber);
    PrivateProfileDTO privateProfile = canSeePrivateProfile(student, viewer) ? getPrivateProfile(student) : null;
    return StudentProfileDTO.from(student, getTutorBadges(student), getAchievements(student), privateProfile);
  }

  @Transactional
  public StudentProfileDTO updateProfile(Long studentNumber, UpdateProfileRequestDTO changes) {
    Student student = getByNumber(studentNumber);
    student.setBanner(changes.banner());
    student.setAboutMe(changes.aboutMe());
    student.setSkills(changes.skills());
    student.setInterests(changes.interests());
    student.setLanguages(changes.languages());
    studentRepository.save(student);

    return StudentProfileDTO.from(student, getTutorBadges(student), getAchievements(student),
        getPrivateProfile(student));
  }

  private Student getByNumber(Long studentNumber) {
    return studentRepository.findById(studentNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  private boolean canSeePrivateProfile(Student student, User viewer) {
    boolean isOwnProfile = viewer.getNumber().equals(student.getNumber());
    boolean isStaff = viewer.getRole() == Role.STAFF;
    boolean isTheirTeacher = viewer.getRole() == Role.TEACHER
        && enrolmentRepository.existsByStudentAndSubjectEdition_Teachers_Number(student, viewer.getNumber());

    return isOwnProfile || isStaff || isTheirTeacher;
  }

  private PrivateProfileDTO getPrivateProfile(Student student) {
    List<Enrolment> enrolments = enrolmentRepository.findByStudent(student);

    return new PrivateProfileDTO(student.getInstitutionalEmail(), student.getDegree().getName(),
        student.getCurricularYear(), PersonalityProfileDTO.from(student.getPersonalityProfile()),
        getAttendance(student, enrolments), !enrolments.isEmpty());
  }

  private Double getAttendance(Student student, List<Enrolment> enrolments) {
    if (student.hasActiveStatute()) {
      return null;
    }

    long lessons = 0;
    long absences = 0;
    for (Enrolment enrolment : enrolments) {
      lessons += lessonRepository.countByClassSection(enrolment.getClassSection());
      absences += absenceRepository.countByEnrolmentAndJustifiedFalse(enrolment);
    }

    if (lessons == 0) {
      return null;
    }
    return 100.0 * (lessons - absences) / lessons;
  }

  private List<TutorBadgeDTO> getTutorBadges(Student student) {
    return tutorRoleRepository.findByStudent(student).stream()
        .map(this::toTutorBadge)
        .toList();
  }

  private TutorBadgeDTO toTutorBadge(TutorRole tutorRole) {
    List<Rating> ratings = ratingRepository.findByParticipation_StudyRoom_TutorRole(tutorRole);
    OptionalDouble average = ratings.stream().mapToInt(Rating::getStars).average();
    Double averageRating = average.isPresent() ? average.getAsDouble() : null;

    return new TutorBadgeDTO(tutorRole.getSubject().getName(), averageRating, ratings.size());
  }

  private List<AchievementBadgeDTO> getAchievements(Student student) {
    return earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(student).stream()
        .map(AchievementBadgeDTO::from)
        .toList();
  }

}
