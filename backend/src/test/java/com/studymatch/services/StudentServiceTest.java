package com.studymatch.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.Language;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Degree;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.RatingRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TutorRoleRepository;

class StudentServiceTest {

  private static final Long MARIA = 50001L;
  private static final Long JOAO = 50002L;
  private static final Long TEACHER = 50003L;
  private static final Long STAFF = 50004L;

  private final StudentRepository studentRepository = mock(StudentRepository.class);
  private final EnrolmentRepository enrolmentRepository = mock(EnrolmentRepository.class);
  private final LessonRepository lessonRepository = mock(LessonRepository.class);
  private final AbsenceRepository absenceRepository = mock(AbsenceRepository.class);
  private final TutorRoleRepository tutorRoleRepository = mock(TutorRoleRepository.class);
  private final RatingRepository ratingRepository = mock(RatingRepository.class);
  private final EarnedAchievementRepository earnedAchievementRepository = mock(EarnedAchievementRepository.class);

  private final StudentService studentService = new StudentService(studentRepository, enrolmentRepository,
      lessonRepository, absenceRepository, tutorRoleRepository, ratingRepository, earnedAchievementRepository);

  private Student maria;

  @BeforeEach
  void createMaria() {
    maria = studentWithNumber(MARIA, "Maria Costa");
    maria.setLanguages(Set.of(Language.PORTUGUESE));
    when(studentRepository.findById(MARIA)).thenReturn(Optional.of(maria));
  }

  @Test
  void showsTheWholeProfileToTheStudent() {
    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertThat(profile.name()).isEqualTo("Maria Costa");
    assertThat(profile.privateProfile()).isNotNull();
    assertThat(profile.privateProfile().curricularYear()).isEqualTo(2);
  }

  @Test
  void showsOnlyThePublicPartToAnotherStudent() {
    Student joao = studentWithNumber(JOAO, "Joao Silva");

    StudentProfileDTO profile = studentService.getProfile(MARIA, joao);

    assertThat(profile.name()).isEqualTo("Maria Costa");
    assertThat(profile.languages()).containsExactly(Language.PORTUGUESE);
    assertThat(profile.privateProfile()).isNull();
  }

  @Test
  void showsTheWholeProfileToStaff() {
    Staff staff = new Staff("staff@example.com", "staff@studymatch.com", "Staff Member", "secret");
    ReflectionTestUtils.setField(staff, "number", STAFF);

    assertThat(studentService.getProfile(MARIA, staff).privateProfile()).isNotNull();
  }

  @Test
  void showsTheWholeProfileOnlyToTheStudentsOwnTeachers() {
    Teacher teacher = new Teacher("teacher@example.com", "teacher@studymatch.com", "Teacher", "secret");
    ReflectionTestUtils.setField(teacher, "number", TEACHER);

    assertThat(studentService.getProfile(MARIA, teacher).privateProfile()).isNull();

    when(enrolmentRepository.existsByStudentAndSubjectEdition_Teachers_Number(maria, TEACHER)).thenReturn(true);
    assertThat(studentService.getProfile(MARIA, teacher).privateProfile()).isNotNull();
  }

  @Test
  void countsJustifiedAbsencesAsAttended() {
    Enrolment enrolment = mock(Enrolment.class);
    when(enrolment.getClassSection()).thenReturn(mock(ClassSection.class));
    when(enrolmentRepository.findByStudent(maria)).thenReturn(List.of(enrolment));
    when(lessonRepository.countByClassSection(any())).thenReturn(20L);
    when(absenceRepository.countByEnrolmentAndJustifiedFalse(enrolment)).thenReturn(5L);

    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertThat(profile.privateProfile().attendance()).isEqualTo(75.0);
    assertThat(profile.privateProfile().hasAcademicHistory()).isTrue();
  }

  @Test
  void hasNoAttendanceWithoutAcademicHistory() {
    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertThat(profile.privateProfile().attendance()).isNull();
    assertThat(profile.privateProfile().hasAcademicHistory()).isFalse();
  }

  @Test
  void hasNoAttendanceWithAnActiveSpecialStatute() {
    maria.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(6));
    Enrolment enrolment = mock(Enrolment.class);
    when(enrolmentRepository.findByStudent(maria)).thenReturn(List.of(enrolment));
    when(lessonRepository.countByClassSection(any())).thenReturn(20L);

    assertThat(studentService.getProfile(MARIA, maria).privateProfile().attendance()).isNull();
  }

  @Test
  void updatesTheEditableFields() {
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO(null, "I like databases.", Set.of("Java"),
        Set.of("Chess"), Set.of(Language.ENGLISH, Language.SPANISH));

    StudentProfileDTO profile = studentService.updateProfile(MARIA, changes);

    assertThat(profile.aboutMe()).isEqualTo("I like databases.");
    assertThat(profile.skills()).containsExactly("Java");
    assertThat(profile.interests()).containsExactly("Chess");
    assertThat(profile.languages()).containsExactlyInAnyOrder(Language.ENGLISH, Language.SPANISH);
  }

  @Test
  void requiresAtLeastOneLanguage() {
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO(null, null, Set.of(), Set.of(), Set.of());

    assertThatThrownBy(() -> studentService.updateProfile(MARIA, changes))
        .isInstanceOfSatisfying(StudyMatchException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.AT_LEAST_ONE_LANGUAGE_REQUIRED));
  }

  @Test
  void reportsAStudentThatDoesNotExist() {
    assertThatThrownBy(() -> studentService.getProfile(99999L, maria))
        .isInstanceOfSatisfying(StudyMatchException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
  }

  private static Student studentWithNumber(Long number, String name) {
    Student student = new Student(name + "@example.com", name, "secret", new Degree("Informatics"), 2);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

}
