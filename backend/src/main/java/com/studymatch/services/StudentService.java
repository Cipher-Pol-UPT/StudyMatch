package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.dtos.PrivateProfileDTO;
import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.repositories.StudentRepository;

@Service
public class StudentService {

  private final StudentRepository studentRepository;
  private final EnrolmentService enrolmentService;
  private final TutorRoleService tutorRoleService;
  private final EarnedAchievementService earnedAchievementService;

  public StudentService(StudentRepository studentRepository, EnrolmentService enrolmentService,
      TutorRoleService tutorRoleService, EarnedAchievementService earnedAchievementService) {
    this.studentRepository = studentRepository;
    this.enrolmentService = enrolmentService;
    this.tutorRoleService = tutorRoleService;
    this.earnedAchievementService = earnedAchievementService;
  }

  @Transactional(readOnly = true)
  public StudentProfileDTO getProfile(Long studentNumber, User viewer) {
    Student student = getByNumber(studentNumber);
    PrivateProfileDTO privateProfile = canSeePrivateProfile(student, viewer) ? getPrivateProfile(student) : null;
    return toProfile(student, privateProfile);
  }

  @Transactional
  public StudentProfileDTO updateProfile(Long studentNumber, UpdateProfileRequestDTO changes) {
    Student student = getByNumber(studentNumber);
    student.setBanner(changes.banner());
    student.setPfp(changes.pfp());
    student.setAboutMe(changes.aboutMe());
    student.setSkills(changes.skills());
    student.setInterests(changes.interests());
    student.setLanguages(changes.languages());
    studentRepository.save(student);

    return toProfile(student, getPrivateProfile(student));
  }

  @Transactional
  public PersonalityProfileDTO takePersonalityTest(Long studentNumber, List<Integer> answers) {
    Student student = getByNumber(studentNumber);
    // GR1: once per academic year, the reset comes with Start Academic Year (#66)
    if (!student.requiresPersonalityTest()) {
      throw new StudyMatchException(ErrorCode.PERSONALITY_TEST_ALREADY_TAKEN);
    }
    student.setPersonalityProfile(PersonalityProfile.fromAnswers(answers));
    return PersonalityProfileDTO.from(studentRepository.save(student).getPersonalityProfile());
  }

  private Student getByNumber(Long studentNumber) {
    return studentRepository.findById(studentNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  private boolean canSeePrivateProfile(Student student, User viewer) {
    boolean isOwnProfile = viewer.getNumber().equals(student.getNumber());
    boolean isStaff = viewer.getRole() == Role.STAFF;
    boolean isTheirTeacher = viewer.getRole() == Role.TEACHER
        && enrolmentService.isTaughtBy(student, viewer.getNumber());

    return isOwnProfile || isStaff || isTheirTeacher;
  }

  private PrivateProfileDTO getPrivateProfile(Student student) {
    Double attendance = student.hasActiveStatute() ? null : enrolmentService.getAttendanceRate(student);

    return new PrivateProfileDTO(student.getInstitutionalEmail(), student.getDegree().getName(),
        student.getCurricularYear(), PersonalityProfileDTO.from(student.getPersonalityProfile()), attendance,
        enrolmentService.hasAcademicHistory(student));
  }

  private StudentProfileDTO toProfile(Student student, PrivateProfileDTO privateProfile) {
    return StudentProfileDTO.from(student, tutorRoleService.getTutorBadges(student),
        earnedAchievementService.getAchievements(student), privateProfile);
  }

}
