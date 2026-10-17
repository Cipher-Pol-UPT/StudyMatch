package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.AchievementBadgeDTO;
import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.enums.Language;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Admin;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.StudentRepository;

class StudentServiceTest {

  private static final Long STUDENT_NUMBER = 50001L;
  private static final Long OTHER_STUDENT_NUMBER = 50002L;
  private static final Long TEACHER_NUMBER = 50003L;
  private static final Long STAFF_NUMBER = 50004L;
  private static final Long ADMIN_NUMBER = 50005L;
  private static final String PNG_BANNER = "data:image/png;base64,iVBORw0KGgo=";

  private StudentRepository studentRepository;
  private EnrolmentService enrolmentService;
  private TutorRoleService tutorRoleService;
  private EarnedAchievementService earnedAchievementService;
  private StudentService studentService;

  private Student student;

  @BeforeEach
  void setUp() {
    studentRepository = mock(StudentRepository.class);
    enrolmentService = mock(EnrolmentService.class);
    tutorRoleService = mock(TutorRoleService.class);
    earnedAchievementService = mock(EarnedAchievementService.class);
    studentService = new StudentService(studentRepository, enrolmentService, tutorRoleService,
        earnedAchievementService);

    student = withNumber(ModelTestData.student("Test Student"), STUDENT_NUMBER);
    student.setLanguages(Set.of(Language.PORTUGUESE));
    student.setPersonalityProfile(new PersonalityProfile(0.8, 0.6, 0.4, 0.7));
    when(studentRepository.findById(STUDENT_NUMBER)).thenReturn(Optional.of(student));
  }

  @Test
  @DisplayName("A student sees their whole profile")
  void studentSeesTheirWholeProfile() {
    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, student);

    // Assert
    assertEquals("Test Student", profile.name());
    assertNotNull(profile.privateProfile());
    assertEquals(1, profile.privateProfile().curricularYear());
    assertEquals(0.8, profile.privateProfile().personalityProfile().leadership());
  }

  @Test
  @DisplayName("Another student sees only the public part")
  void anotherStudentSeesOnlyThePublicPart() {
    // Arrange
    Student otherStudent = withNumber(ModelTestData.student("Other Test Student"), OTHER_STUDENT_NUMBER);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, otherStudent);

    // Assert
    assertEquals("Test Student", profile.name());
    assertEquals(Set.of(Language.PORTUGUESE), profile.languages());
    assertNull(profile.privateProfile());
  }

  @Test
  @DisplayName("Staff see the whole profile")
  void staffSeeTheWholeProfile() {
    // Arrange
    Staff staff = withNumber(new Staff("staff@example.com", "staff@test.com", "Test Staff", "hash"), STAFF_NUMBER);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, staff);

    // Assert
    assertNotNull(profile.privateProfile());
  }

  @Test
  @DisplayName("An admin sees only the public part")
  void adminSeesOnlyThePublicPart() {
    // Arrange
    Admin admin = withNumber(new Admin("admin@example.com", "admin@test.com", "Test Admin", "hash"), ADMIN_NUMBER);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, admin);

    // Assert
    assertNull(profile.privateProfile());
  }

  @Test
  @DisplayName("A teacher of the student sees the whole profile")
  void theirTeacherSeesTheWholeProfile() {
    // Arrange
    Teacher teacher = withNumber(ModelTestData.teacher(), TEACHER_NUMBER);
    when(enrolmentService.isTaughtBy(student, TEACHER_NUMBER)).thenReturn(true);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, teacher);

    // Assert
    assertNotNull(profile.privateProfile());
  }

  @Test
  @DisplayName("A teacher who doesn't teach the student sees only the public part")
  void otherTeacherSeesOnlyThePublicPart() {
    // Arrange
    Teacher teacher = withNumber(ModelTestData.teacher(), TEACHER_NUMBER);
    when(enrolmentService.isTaughtBy(student, TEACHER_NUMBER)).thenReturn(false);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, teacher);

    // Assert
    assertNull(profile.privateProfile());
  }

  @Test
  @DisplayName("The private part shows the attendance and the academic history")
  void privatePartShowsAttendanceAndHistory() {
    // Arrange
    when(enrolmentService.getAttendanceRate(student)).thenReturn(75.0);
    when(enrolmentService.hasAcademicHistory(student)).thenReturn(true);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, student);

    // Assert
    assertEquals(75.0, profile.privateProfile().attendance());
    assertTrue(profile.privateProfile().hasAcademicHistory());
  }

  @Test
  @DisplayName("A student without academic history has no attendance")
  void noAcademicHistoryHasNoAttendance() {
    // Arrange
    when(enrolmentService.getAttendanceRate(student)).thenReturn(null);
    when(enrolmentService.hasAcademicHistory(student)).thenReturn(false);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, student);

    // Assert
    assertNull(profile.privateProfile().attendance());
    assertFalse(profile.privateProfile().hasAcademicHistory());
  }

  @Test
  @DisplayName("A student with an active special statute has no attendance (GR7)")
  void activeStatuteHasNoAttendance() {
    // Arrange
    student.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(6));

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, student);

    // Assert
    assertNull(profile.privateProfile().attendance());
    verify(enrolmentService, never()).getAttendanceRate(any());
  }

  @Test
  @DisplayName("The public part shows the tutor badges and the achievements")
  void publicPartShowsBadges() {
    // Arrange
    List<TutorBadgeDTO> tutorBadges = List.of(new TutorBadgeDTO("Test Subject", 4.0, 3));
    List<AchievementBadgeDTO> achievements = List.of(
        new AchievementBadgeDTO(Achievement.FIRST_STUDYROOM, LocalDateTime.of(2026, 10, 1, 12, 0)));
    when(tutorRoleService.getTutorBadges(student)).thenReturn(tutorBadges);
    when(earnedAchievementService.getAchievements(student)).thenReturn(achievements);
    Student otherStudent = withNumber(ModelTestData.student("Other Test Student"), OTHER_STUDENT_NUMBER);

    // Act
    StudentProfileDTO profile = studentService.getProfile(STUDENT_NUMBER, otherStudent);

    // Assert
    assertSame(tutorBadges, profile.tutorBadges());
    assertSame(achievements, profile.achievements());
  }

  @Test
  @DisplayName("An unknown student number throws NOT_FOUND")
  void unknownStudentThrowsNotFound() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.getProfile(99999L, student));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("Editing saves the banner, about me, skills, interests and languages")
  void editingSavesTheEditableFields() {
    // Arrange
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO(PNG_BANNER, "Test about me",
        Set.of("Test Skill"), Set.of("Test Interest"), Set.of(Language.ENGLISH, Language.SPANISH));

    // Act
    StudentProfileDTO profile = studentService.updateProfile(STUDENT_NUMBER, changes);

    // Assert
    assertEquals(PNG_BANNER, profile.banner());
    assertEquals("Test about me", profile.aboutMe());
    assertEquals(Set.of("Test Skill"), profile.skills());
    assertEquals(Set.of("Test Interest"), profile.interests());
    assertEquals(Set.of(Language.ENGLISH, Language.SPANISH), profile.languages());
    assertNotNull(profile.privateProfile());
    verify(studentRepository).save(student);
  }

  @Test
  @DisplayName("Editing with no languages throws AT_LEAST_ONE_LANGUAGE_REQUIRED")
  void editingWithoutLanguagesIsRejected() {
    // Arrange
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO(null, null, Set.of(), Set.of(), Set.of());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.updateProfile(STUDENT_NUMBER, changes));

    // Assert
    assertEquals(ErrorCode.AT_LEAST_ONE_LANGUAGE_REQUIRED, e.getCode());
    verify(studentRepository, never()).save(any());
  }

  @Test
  @DisplayName("Editing with an invalid banner throws INVALID_BANNER")
  void editingWithAnInvalidBannerIsRejected() {
    // Arrange
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO("data:image/gif;base64,R0lGODlh", null,
        Set.of(), Set.of(), Set.of(Language.ENGLISH));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.updateProfile(STUDENT_NUMBER, changes));

    // Assert
    assertEquals(ErrorCode.INVALID_BANNER, e.getCode());
    verify(studentRepository, never()).save(any());
  }

  private static <T extends User> T withNumber(T user, Long number) {
    ReflectionTestUtils.setField(user, "number", number);
    return user;
  }

}
