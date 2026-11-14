package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.dtos.EditStudentRequestDTO;
import com.studymatch.dtos.StudentRecordDTO;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.utils.EmailSender;

import jakarta.validation.Validator;

class EditStudentTest {

  private static final Long STUDENT_NUMBER = 50001L;
  private static final Long OLD_DEGREE_ID = 1L;
  private static final Long NEW_DEGREE_ID = 2L;
  private static final Long UNKNOWN_DEGREE_ID = 99L;

  private StudentRepository studentRepository;
  private DegreeService degreeService;
  private UserService userService;
  private StudentService studentService;

  private Student student;
  private Staff staff;
  private final Degree oldDegree = new Degree("Test Degree", 5);
  private final Degree newDegree = new Degree("Other Test Degree", 3);

  @BeforeEach
  void setUp() {
    studentRepository = mock(StudentRepository.class);
    degreeService = mock(DegreeService.class);
    userService = mock(UserService.class);
    // the registration dependencies aren't used here (see StudentRegistrationTest)
    studentService = new StudentService(studentRepository, mock(EnrolmentService.class), mock(TutorRoleService.class),
        mock(EarnedAchievementService.class), degreeService, userService, mock(PasswordEncoder.class),
        mock(EmailSender.class), mock(Validator.class), mock(TransactionTemplate.class), "test.com");

    student = new Student("ana@example.com", "Ana Test", "hash", oldDegree, 4);
    ReflectionTestUtils.setField(student, "number", STUDENT_NUMBER);
    staff = new Staff("staff@example.com", "staff@test.com", "Test Staff", "hash");
    ReflectionTestUtils.setField(staff, "number", 50002L);

    when(studentRepository.findById(STUDENT_NUMBER)).thenReturn(Optional.of(student));
    when(studentRepository.findById(404L)).thenReturn(Optional.empty());
    when(degreeService.findDegree(OLD_DEGREE_ID)).thenReturn(Optional.of(oldDegree));
    when(degreeService.findDegree(NEW_DEGREE_ID)).thenReturn(Optional.of(newDegree));
    when(degreeService.findDegree(UNKNOWN_DEGREE_ID)).thenReturn(Optional.empty());
    when(userService.isPersonalEmailTaken(any())).thenReturn(false);
  }

  private static EditStudentRequestDTO changes(String email, Long degreeId, int year) {
    return new EditStudentRequestDTO("Ana Edited", email, degreeId, year);
  }

  private StudyMatchException editFails(Long number, EditStudentRequestDTO changes) {
    return assertThrows(StudyMatchException.class, () -> studentService.editStudent(number, changes, staff));
  }

  // nothing saved and the student still has the old data
  private void assertNothingChanged() {
    verify(studentRepository, never()).saveAndFlush(any());
    assertEquals("Ana Test", student.getName());
    assertEquals("ana@example.com", student.getPersonalEmail());
    assertSame(oldDegree, student.getDegree());
    assertEquals(4, student.getCurricularYear());
  }

  @Test
  @DisplayName("Name, personal email, degree and year are changed, saved and sent back in the staff record")
  void editsStudent() {
    // Act
    StudentRecordDTO record = studentService.editStudent(STUDENT_NUMBER,
        changes("ana.new@example.com", NEW_DEGREE_ID, 2), staff);

    // Assert
    verify(studentRepository).saveAndFlush(student);
    assertEquals("Ana Edited", record.profile().name());
    assertEquals("ana.new@example.com", record.personalEmail());
    assertEquals("Other Test Degree", record.profile().privateProfile().degree());
    assertEquals(2, record.profile().privateProfile().curricularYear());
  }

  @Test
  @DisplayName("The number, institutional email, profile and statute aren't touched")
  void keepsEverythingElse() {
    // Arrange
    student.generateInstitutionalEmail("test.com");
    student.setAboutMe("Test about me");
    student.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusDays(30));

    // Act
    studentService.editStudent(STUDENT_NUMBER, changes("ana.new@example.com", NEW_DEGREE_ID, 2), staff);

    // Assert
    assertEquals(STUDENT_NUMBER, student.getNumber());
    assertEquals("50001@students.test.com", student.getInstitutionalEmail());
    assertEquals("Test about me", student.getAboutMe());
    assertEquals(StatuteType.WORKING_STUDENT, student.getStatuteType());
  }

  @Test
  @DisplayName("An unknown student is NOT_FOUND")
  void unknownStudent() {
    // Act
    StudyMatchException e = editFails(404L, changes("ana.new@example.com", NEW_DEGREE_ID, 2));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
    verify(studentRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("An unknown degree is DEGREE_NOT_FOUND and nothing changes")
  void unknownDegree() {
    // Act
    StudyMatchException e = editFails(STUDENT_NUMBER, changes("ana.new@example.com", UNKNOWN_DEGREE_ID, 2));

    // Assert
    assertEquals(ErrorCode.DEGREE_NOT_FOUND, e.getCode());
    assertNothingChanged();
  }

  @Test
  @DisplayName("The last year of the new degree is fine (boundary)")
  void lastYearOfDegree() {
    // Act
    studentService.editStudent(STUDENT_NUMBER, changes("ana@example.com", NEW_DEGREE_ID, 3), staff);

    // Assert
    assertEquals(3, student.getCurricularYear());
    verify(studentRepository).saveAndFlush(student);
  }

  @Test
  @DisplayName("One year past the new degree's last is CURRICULAR_YEAR_NOT_IN_DEGREE with its years (boundary)")
  void yearNotInDegree() {
    // Act
    StudyMatchException e = editFails(STUDENT_NUMBER, changes("ana@example.com", NEW_DEGREE_ID, 4));

    // Assert
    assertEquals(ErrorCode.CURRICULAR_YEAR_NOT_IN_DEGREE, e.getCode());
    assertEquals(Map.of("years", 3), e.getParams());
    assertNothingChanged();
  }

  @Test
  @DisplayName("The year is checked against the degree sent, not the one the student is in now")
  void yearCheckedAgainstNewDegree() {
    // Arrange - in the 3 year degree now, so year 5 doesn't exist there
    student.setDegree(newDegree);
    student.setCurricularYear(3);

    // Act - moving to the 5 year degree
    studentService.editStudent(STUDENT_NUMBER, changes("ana@example.com", OLD_DEGREE_ID, 5), staff);

    // Assert
    assertSame(oldDegree, student.getDegree());
    assertEquals(5, student.getCurricularYear());
  }

  @Test
  @DisplayName("An email someone else has is PERSONAL_EMAIL_ALREADY_EXISTS and nothing changes")
  void emailTaken() {
    // Arrange
    when(userService.isPersonalEmailTaken("taken@example.com")).thenReturn(true);

    // Act
    StudyMatchException e = editFails(STUDENT_NUMBER, changes("taken@example.com", OLD_DEGREE_ID, 4));

    // Assert
    assertEquals(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS, e.getCode());
    assertNothingChanged();
  }

  @Test
  @DisplayName("Saving with their own email unchanged doesn't count as taken")
  void ownEmailKept() {
    // Arrange - the db would say yes, it's this student's own email
    when(userService.isPersonalEmailTaken("ana@example.com")).thenReturn(true);

    // Act
    studentService.editStudent(STUDENT_NUMBER, changes("ana@example.com", OLD_DEGREE_ID, 4), staff);

    // Assert
    assertEquals("Ana Edited", student.getName());
    verify(userService, never()).isPersonalEmailTaken(any());
    verify(studentRepository).saveAndFlush(student);
  }

  @Test
  @DisplayName("Their own email saved with different capitals is still their own")
  void ownEmailDifferentCapitals() {
    // Arrange - an older row saved before emails were lowercased
    student.setPersonalEmail("Ana@Example.com");

    // Act
    studentService.editStudent(STUDENT_NUMBER, changes("ana@example.com", OLD_DEGREE_ID, 4), staff);

    // Assert
    verify(userService, never()).isPersonalEmailTaken(any());
    assertEquals("ana@example.com", student.getPersonalEmail());
  }

  @Test
  @DisplayName("If someone else saves the same email first, the unique column's error becomes PERSONAL_EMAIL_ALREADY_EXISTS")
  void simultaneousEmailIsRefused() {
    // Arrange
    when(studentRepository.saveAndFlush(any(Student.class))).thenThrow(new DataIntegrityViolationException("unique"));

    // Act
    StudyMatchException e = editFails(STUDENT_NUMBER, changes("ana.new@example.com", OLD_DEGREE_ID, 4));

    // Assert
    assertEquals(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("The degree is checked before the email, so a wrong degree is reported first")
  void degreeCheckedFirst() {
    // Arrange
    when(userService.isPersonalEmailTaken("taken@example.com")).thenReturn(true);

    // Act
    StudyMatchException e = editFails(STUDENT_NUMBER, changes("taken@example.com", UNKNOWN_DEGREE_ID, 2));

    // Assert
    assertEquals(ErrorCode.DEGREE_NOT_FOUND, e.getCode());
  }

}
