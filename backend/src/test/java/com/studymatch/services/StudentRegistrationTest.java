package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.dtos.FieldErrorDTO;
import com.studymatch.dtos.ImportPreviewRowDTO;
import com.studymatch.dtos.ImportResultRowDTO;
import com.studymatch.dtos.ImportResultRowDTO.Status;
import com.studymatch.dtos.ImportStudentsRequestDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;
import com.studymatch.dtos.RegisteredStudentDTO;
import com.studymatch.dtos.ResendCredentialsRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.models.Student;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.utils.EmailSender;
import com.studymatch.utils.StudentCsvReader;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class StudentRegistrationTest {

  private static Validator validator;

  private StudentRepository studentRepository;
  private DegreeService degreeService;
  private UserService userService;
  private PasswordEncoder passwordEncoder;
  private EmailSender emailSender;
  private TransactionTemplate transactionTemplate;
  private StudentService studentService;

  private final Degree threeYears = new Degree("Test Degree", 3);
  private final List<Student> saved = new ArrayList<>();

  @BeforeAll
  static void createValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @BeforeEach
  void setUp() {
    studentRepository = mock(StudentRepository.class);
    degreeService = mock(DegreeService.class);
    userService = mock(UserService.class);
    passwordEncoder = new BCryptPasswordEncoder(4);
    emailSender = mock(EmailSender.class);
    transactionTemplate = mock(TransactionTemplate.class);

    // saving works like the db: new students get the next number, starting at 50000
    when(studentRepository.save(any(Student.class))).thenAnswer(call -> {
      Student student = call.getArgument(0);
      if (student.getNumber() == null) {
        ReflectionTestUtils.setField(student, "number", 50000L + saved.size());
        saved.add(student);
      }
      return student;
    });
    // no real transactions in a unit test, the code inside just runs
    when(transactionTemplate.execute(any())).thenAnswer(call -> ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
    when(degreeService.findByName("Test Degree")).thenReturn(Optional.of(threeYears));
    when(degreeService.getByName("Test Degree")).thenReturn(threeYears);

    studentService = new StudentService(studentRepository, mock(EnrolmentService.class), mock(TutorRoleService.class),
        mock(EarnedAchievementService.class), degreeService, userService, passwordEncoder, emailSender, validator,
        transactionTemplate, "test.com");
  }

  private static RegisterStudentRequestDTO student(String email) {
    return new RegisterStudentRequestDTO("Test Student", email, "Test Degree", 1);
  }

  private ErrorCode registerFails(RegisterStudentRequestDTO request) {
    return assertThrows(StudyMatchException.class, () -> studentService.registerStudent(request)).getCode();
  }

  // the one-time password that went in the (fake) email
  private String sentPassword() {
    ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
    verify(emailSender).sendAccountCredentials(any(), any(), any(), password.capture());
    return password.getValue();
  }

  private static List<String> codes(List<FieldErrorDTO> errors) {
    return errors.stream().map(e -> e.field() + "=" + e.code()).toList();
  }

  // manual registration

  @Test
  @DisplayName("A registered student gets number@students.domain, a hashed one-time password and an email")
  void registerStudent() {
    // Act
    RegisteredStudentDTO result = studentService.registerStudent(student("new.student@example.com"));

    // Assert
    assertEquals(50000L, result.number());
    assertEquals("50000@students.test.com", result.institutionalEmail());
    assertTrue(result.emailSent());
    Student created = saved.getFirst();
    assertTrue(created.isMustChangePassword());
    assertTrue(passwordEncoder.matches(sentPassword(), created.getPassword()));
    verify(emailSender).sendAccountCredentials(eq("new.student@example.com"), eq("Test Student"),
        eq("50000@students.test.com"), anyString());
  }

  @Test
  @DisplayName("If the email fails the account is kept and the answer says so (staff can resend)")
  void emailFailsKeepsAccount() {
    // Arrange
    doThrow(new MailSendException("mail server down")).when(emailSender).sendAccountCredentials(any(), any(), any(), any());

    // Act
    RegisteredStudentDTO result = studentService.registerStudent(student("new.student@example.com"));

    // Assert
    assertFalse(result.emailSent());
    assertEquals(1, saved.size());
  }

  @Test
  @DisplayName("An unknown degree is DEGREE_NOT_FOUND and nothing is saved")
  void unknownDegree() {
    // Act
    ErrorCode code = registerFails(new RegisterStudentRequestDTO("Test Student", "s@example.com", "Nope", 1));

    // Assert
    assertEquals(ErrorCode.DEGREE_NOT_FOUND, code);
    verify(studentRepository, never()).save(any());
  }

  @Test
  @DisplayName("The last year of the degree is fine (boundary: 3 of 3)")
  void lastYearOfDegree() {
    assertTrue(studentService.registerStudent(new RegisterStudentRequestDTO("Test Student", "s@example.com", "Test Degree", 3))
        .emailSent());
  }

  @Test
  @DisplayName("One year past the degree is refused and says how many years it has (boundary: 4 of 3)")
  void yearPastDegree() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.registerStudent(new RegisterStudentRequestDTO("Test Student", "s@example.com", "Test Degree", 4)));

    // Assert
    assertEquals(ErrorCode.CURRICULAR_YEAR_NOT_IN_DEGREE, e.getCode());
    assertEquals(Map.of("years", 3), e.getParams());
  }

  @Test
  @DisplayName("A personal email that's already used is refused")
  void personalEmailTaken() {
    // Arrange
    when(userService.isPersonalEmailTaken("taken@example.com")).thenReturn(true);

    // Act + Assert
    assertEquals(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS, registerFails(student("taken@example.com")));
    verify(studentRepository, never()).save(any());
  }

  @Test
  @DisplayName("If someone takes the email right between the check and the save, it's still a 409, not a crash")
  void emailTakenAtTheSameTime() {
    // Arrange - the db's unique constraint is what catches it (doThrow, because when() would run the old answer)
    doThrow(new DataIntegrityViolationException("duplicate key")).when(transactionTemplate).execute(any());

    // Act + Assert
    assertEquals(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS, registerFails(student("new.student@example.com")));
    verify(emailSender, never()).sendAccountCredentials(any(), any(), any(), any());
  }

  // CSV step 1: preview

  @Test
  @DisplayName("The preview marks each row and saves nothing")
  void previewSavesNothing() {
    // Arrange - row 2 is fine, row 3 has no name and a bad email
    List<StudentCsvReader.Row> rows = List.of(
        new StudentCsvReader.Row(2, student("ok@example.com"), List.of()),
        new StudentCsvReader.Row(3, new RegisterStudentRequestDTO("", "not-an-email", "Test Degree", 1), List.of()));

    // Act
    List<ImportPreviewRowDTO> preview = studentService.previewImport(rows);

    // Assert
    assertTrue(preview.get(0).errors().isEmpty());
    assertEquals(3, preview.get(1).row());
    assertEquals(List.of("name=NOT_BLANK", "personalEmail=EMAIL"), codes(preview.get(1).errors()).stream().sorted().toList());
    verify(studentRepository, never()).save(any());
    verify(emailSender, never()).sendAccountCredentials(any(), any(), any(), any());
  }

  @Test
  @DisplayName("The same personal email twice in a file: the second row is DUPLICATE_IN_FILE")
  void duplicateInFile() {
    // Act
    List<ImportPreviewRowDTO> preview = studentService.previewImport(List.of(
        new StudentCsvReader.Row(2, student("same@example.com"), List.of()),
        new StudentCsvReader.Row(3, student("same@example.com"), List.of())));

    // Assert
    assertTrue(preview.get(0).errors().isEmpty());
    assertEquals(List.of("personalEmail=DUPLICATE_IN_FILE"), codes(preview.get(1).errors()));
  }

  @Test
  @DisplayName("A year that wasn't a number shows only INVALID, not also 'required'")
  void readErrorWins() {
    // Arrange - what the reader gives for "two"
    StudentCsvReader.Row row = new StudentCsvReader.Row(2,
        new RegisterStudentRequestDTO("Test Student", "s@example.com", "Test Degree", null),
        List.of(new FieldErrorDTO("curricularYear", "INVALID", Map.of())));

    // Act + Assert
    assertEquals(List.of("curricularYear=INVALID"), codes(studentService.previewImport(List.of(row)).get(0).errors()));
  }

  @Test
  @DisplayName("Database checks are skipped for fields that are already wrong")
  void noLookupForBlankFields() {
    // Act - blank degree and blank email
    List<FieldErrorDTO> errors = studentService.previewImport(List.of(
        new StudentCsvReader.Row(2, new RegisterStudentRequestDTO("Test Student", "", "", 1), List.of()))).get(0).errors();

    // Assert - just the two NOT_BLANKs, no DEGREE_NOT_FOUND and no lookups
    assertEquals(List.of("degreeProgramme=NOT_BLANK", "personalEmail=NOT_BLANK"), codes(errors).stream().sorted().toList());
    verify(degreeService, never()).findByName(any());
    verify(userService, never()).isPersonalEmailTaken(any());
  }

  // CSV step 2: import the confirmed rows

  private static ImportStudentsRequestDTO.Row confirmed(int row, RegisterStudentRequestDTO student) {
    return new ImportStudentsRequestDTO.Row(row, student);
  }

  @Test
  @DisplayName("Valid rows are created with different numbers, invalid ones are rejected and create nothing")
  void importCreatesValidRows() {
    // Act
    List<ImportResultRowDTO> results = studentService.importStudents(List.of(
        confirmed(2, student("a@example.com")),
        confirmed(3, new RegisterStudentRequestDTO("", "b@example.com", "Test Degree", 1)),
        confirmed(4, student("c@example.com"))));

    // Assert
    assertEquals(List.of(Status.CREATED, Status.REJECTED, Status.CREATED), results.stream().map(ImportResultRowDTO::status).toList());
    assertEquals(List.of("name=NOT_BLANK"), codes(results.get(1).errors()));
    assertEquals(2, saved.size());
    assertNotEquals(results.get(0).number(), results.get(2).number());
  }

  @Test
  @DisplayName("A row whose email fails is created but reported as EMAIL_NOT_SENT, the rest carry on")
  void importEmailFailure() {
    // Arrange - only b's email fails
    doThrow(new MailSendException("mail server down"))
        .when(emailSender).sendAccountCredentials(eq("b@example.com"), any(), any(), any());

    // Act
    List<ImportResultRowDTO> results = studentService.importStudents(List.of(
        confirmed(2, student("a@example.com")), confirmed(3, student("b@example.com")), confirmed(4, student("c@example.com"))));

    // Assert
    assertEquals(List.of(Status.CREATED, Status.EMAIL_NOT_SENT, Status.CREATED), results.stream().map(ImportResultRowDTO::status).toList());
    assertEquals(3, saved.size());
    assertEquals("50001@students.test.com", results.get(1).institutionalEmail());
  }

  @Test
  @DisplayName("Rows are checked again on import, even if the preview said they were fine")
  void importRevalidates() {
    // Arrange - someone registered this email after the preview
    when(userService.isPersonalEmailTaken("late@example.com")).thenReturn(true);

    // Act
    ImportResultRowDTO result = studentService.importStudents(List.of(confirmed(2, student("late@example.com")))).get(0);

    // Assert
    assertEquals(Status.REJECTED, result.status());
    assertEquals(List.of("personalEmail=PERSONAL_EMAIL_ALREADY_EXISTS"), codes(result.errors()));
    assertTrue(saved.isEmpty());
  }

  @Test
  @DisplayName("The same email twice in the confirmed rows only creates one account")
  void importDuplicate() {
    // Act
    List<ImportResultRowDTO> results = studentService.importStudents(List.of(
        confirmed(2, student("same@example.com")), confirmed(3, student("same@example.com"))));

    // Assert
    assertEquals(List.of(Status.CREATED, Status.REJECTED), results.stream().map(ImportResultRowDTO::status).toList());
    assertEquals(1, saved.size());
  }

  @Test
  @DisplayName("A row without a student is rejected instead of crashing")
  void importRowWithoutStudent() {
    ImportResultRowDTO result = studentService.importStudents(List.of(confirmed(2, null))).get(0);
    assertEquals(Status.REJECTED, result.status());
    assertEquals(List.of("student=NOT_NULL"), codes(result.errors()));
  }

  @Test
  @DisplayName("More than 200 confirmed rows is CSV_TOO_MANY_ROWS, nothing created")
  void importTooManyRows() {
    // Arrange
    List<ImportStudentsRequestDTO.Row> rows = new ArrayList<>();
    for (int i = 0; i <= StudentCsvReader.MAX_ROWS; i++) {
      rows.add(confirmed(i + 2, student("s" + i + "@example.com")));
    }

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> studentService.importStudents(rows));

    // Assert
    assertEquals(ErrorCode.CSV_TOO_MANY_ROWS, e.getCode());
    assertTrue(saved.isEmpty());
  }

  // resend credentials

  private Student registered(String email) {
    studentService.registerStudent(student(email));
    Student student = saved.getFirst();
    when(studentRepository.findById(student.getNumber())).thenReturn(Optional.of(student));
    return student;
  }

  @Test
  @DisplayName("Resending gives a new one-time password and the old one stops working")
  void resendNewPassword() {
    // Arrange
    Student student = registered("s@example.com");
    String firstPassword = sentPassword();

    // Act
    studentService.resendCredentials(student.getNumber(), null);

    // Assert
    ArgumentCaptor<String> passwords = ArgumentCaptor.forClass(String.class);
    verify(emailSender, times(2)).sendAccountCredentials(eq("s@example.com"), any(), any(), passwords.capture());
    String secondPassword = passwords.getValue();
    assertNotEquals(firstPassword, secondPassword);
    assertTrue(passwordEncoder.matches(secondPassword, student.getPassword()));
    assertFalse(passwordEncoder.matches(firstPassword, student.getPassword()));
  }

  @Test
  @DisplayName("Resending with a corrected personal email changes it and sends there")
  void resendToCorrectedEmail() {
    // Arrange
    Student student = registered("typo@exmaple.com");

    // Act
    studentService.resendCredentials(student.getNumber(), new ResendCredentialsRequestDTO("fixed@example.com"));

    // Assert
    assertEquals("fixed@example.com", student.getPersonalEmail());
    verify(emailSender).sendAccountCredentials(eq("fixed@example.com"), any(), any(), any());
  }

  @Test
  @DisplayName("The corrected email can't be someone else's")
  void resendToTakenEmail() {
    // Arrange
    Student student = registered("typo@exmaple.com");
    when(userService.isPersonalEmailTaken("someone.else@example.com")).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> studentService.resendCredentials(
        student.getNumber(), new ResendCredentialsRequestDTO("someone.else@example.com")));

    // Assert
    assertEquals(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS, e.getCode());
    assertEquals("typo@exmaple.com", student.getPersonalEmail());
  }

  @Test
  @DisplayName("Once the student set their own password, credentials can't be resent")
  void resendAfterPasswordChanged() {
    // Arrange
    Student student = registered("s@example.com");
    student.setMustChangePassword(false);

    // Act + Assert
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.resendCredentials(student.getNumber(), null));
    assertEquals(ErrorCode.CREDENTIALS_ALREADY_USED, e.getCode());
  }

  @Test
  @DisplayName("If the resend fails too, the staff gets EMAIL_NOT_SENT")
  void resendFails() {
    // Arrange
    Student student = registered("s@example.com");
    doThrow(new MailSendException("mail server down")).when(emailSender).sendAccountCredentials(any(), any(), any(), any());

    // Act + Assert
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.resendCredentials(student.getNumber(), null));
    assertEquals(ErrorCode.EMAIL_NOT_SENT, e.getCode());
  }

  @Test
  @DisplayName("Resending for a student that doesn't exist is NOT_FOUND")
  void resendUnknownStudent() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> studentService.resendCredentials(99999L, null));
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
