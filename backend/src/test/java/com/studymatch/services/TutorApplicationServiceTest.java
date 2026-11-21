package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.ApplyTutorRequestDTO;
import com.studymatch.dtos.PendingTutorApplicationDTO;
import com.studymatch.dtos.TutorApplicationDTO;
import com.studymatch.dtos.TutorEligibleSubjectDTO;
import com.studymatch.dtos.TutorRankingDTO;
import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Enrolment;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.TutorApplicationRepository;

class TutorApplicationServiceTest {

  private static final Long STUDENT_NUMBER = 1L;
  private static final Long SUBJECT_ID = 5L;

  private TutorApplicationRepository tutorApplicationRepository;
  private EnrolmentService enrolmentService;
  private TutorRoleService tutorRoleService;
  private SubjectEditionService subjectEditionService;
  private SubjectService subjectService;
  private TutorApplicationService tutorApplicationService;

  private Student student;

  @BeforeEach
  void setUp() {
    tutorApplicationRepository = mock(TutorApplicationRepository.class);
    enrolmentService = mock(EnrolmentService.class);
    tutorRoleService = mock(TutorRoleService.class);
    subjectEditionService = mock(SubjectEditionService.class);
    subjectService = mock(SubjectService.class);
    tutorApplicationService = new TutorApplicationService(tutorApplicationRepository, enrolmentService, tutorRoleService,
        subjectEditionService, subjectService);
    student = ModelTestData.student("Test Student");
    ReflectionTestUtils.setField(student, "number", STUDENT_NUMBER);
  }

  // a subject the student passed (grade 15), with the edition and subject ids the rules compare against
  private Enrolment passedEnrolment() {
    Enrolment enrolment = ModelTestData.enrolment(student);
    enrolment.setFinalGrade(15);
    SubjectEdition edition = enrolment.getSubjectEdition();
    ReflectionTestUtils.setField(edition, "id", 99L);
    ReflectionTestUtils.setField(edition.getSubject(), "id", SUBJECT_ID);
    return enrolment;
  }

  @Test
  @DisplayName("Blocking a student cancels their pending applications and says how many")
  void cancelPendingApplications() {
    // Arrange
    TutorApplication first = new TutorApplication(student, ModelTestData.subject(), "test motivation");
    TutorApplication second = new TutorApplication(student, ModelTestData.subject(), "test motivation");
    when(tutorApplicationRepository.findByStudentNumberAndStatus(STUDENT_NUMBER, TutorApplicationStatus.PENDING))
        .thenReturn(List.of(first, second));

    // Act
    int cancelled = tutorApplicationService.cancelPending(STUDENT_NUMBER);

    // Assert
    assertEquals(2, cancelled);
    assertEquals(TutorApplicationStatus.CANCELLED, first.getStatus());
    assertEquals(TutorApplicationStatus.CANCELLED, second.getStatus());
    verify(tutorApplicationRepository).saveAll(List.of(first, second));
  }

  @Test
  @DisplayName("A student without pending applications has nothing cancelled")
  void noPendingApplications() {
    // Act - the repository finds nothing by default
    int cancelled = tutorApplicationService.cancelPending(STUDENT_NUMBER);

    // Assert
    assertEquals(0, cancelled);
  }

  @Test
  @DisplayName("Pending applications are counted without changing them")
  void countPendingApplications() {
    // Arrange
    TutorApplication application = new TutorApplication(student, ModelTestData.subject(), "test motivation");
    when(tutorApplicationRepository.findByStudentNumberAndStatus(STUDENT_NUMBER, TutorApplicationStatus.PENDING))
        .thenReturn(List.of(application));

    // Act
    int pending = tutorApplicationService.countPending(STUDENT_NUMBER);

    // Assert
    assertEquals(1, pending);
    assertEquals(TutorApplicationStatus.PENDING, application.getStatus());
  }

  @Test
  @DisplayName("The passed subjects are listed with their rank and eligibility")
  void listsApplicableSubjects() {
    // Arrange
    Enrolment maths = passedEnrolment();
    Subject subject = maths.getSubjectEdition().getSubject();
    AcademicYear year = maths.getSubjectEdition().getAcademicYear();
    when(enrolmentService.getPassedEnrolments(student)).thenReturn(List.of(maths));
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(2, 40, true));
    when(tutorApplicationRepository.findByStudentNumberAndStatus(STUDENT_NUMBER, TutorApplicationStatus.PENDING))
        .thenReturn(List.of());
    when(tutorRoleService.getTutoredSubjects(STUDENT_NUMBER)).thenReturn(List.of());
    when(subjectEditionService.findCurrentYear()).thenReturn(Optional.of(year));
    when(subjectEditionService.hasEdition(subject, year)).thenReturn(true);

    // Act
    List<TutorEligibleSubjectDTO> result = tutorApplicationService.listApplicableSubjects(student);

    // Assert
    assertEquals(1, result.size());
    TutorEligibleSubjectDTO item = result.getFirst();
    assertEquals(SUBJECT_ID, item.subjectId());
    assertEquals("Test Subject", item.subjectName());
    assertEquals(15, item.finalGrade());
    assertEquals(2, item.position());
    assertEquals(40, item.editionSize());
    assertTrue(item.eligible());
    assertFalse(item.hasPendingApplication());
    assertFalse(item.hasTutorRole());
    assertTrue(item.hasCurrentEdition());
  }

  @Test
  @DisplayName("An eligible student applies and the application is Pending")
  void applies() {
    // Arrange
    Enrolment maths = passedEnrolment();
    Subject subject = maths.getSubjectEdition().getSubject();
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(1, 10, true));
    when(subjectEditionService.findCurrentEdition(subject)).thenReturn(Optional.of(maths.getSubjectEdition()));
    when(tutorApplicationRepository.save(any(TutorApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    TutorApplicationDTO result = tutorApplicationService.apply(student,
        new ApplyTutorRequestDTO(SUBJECT_ID, "test motivation"));

    // Assert
    assertEquals(SUBJECT_ID, result.subjectId());
    assertEquals(TutorApplicationStatus.PENDING, result.status());
    verify(tutorApplicationRepository).save(any(TutorApplication.class));
  }

  @Test
  @DisplayName("A student outside the top 5% can't apply")
  void notEligibleCannotApply() {
    // Arrange
    Enrolment maths = passedEnrolment();
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(6, 10, false));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.apply(student, new ApplyTutorRequestDTO(SUBJECT_ID, "test motivation")));

    // Assert
    assertEquals(ErrorCode.NOT_ELIGIBLE_FOR_TUTORING, e.getCode());
  }

  @Test
  @DisplayName("A student with a pending application can't apply again")
  void pendingApplicationCannotApply() {
    // Arrange
    Enrolment maths = passedEnrolment();
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(1, 10, true));
    when(tutorApplicationRepository.existsByStudentNumberAndSubjectIdAndStatus(STUDENT_NUMBER, SUBJECT_ID,
        TutorApplicationStatus.PENDING)).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.apply(student, new ApplyTutorRequestDTO(SUBJECT_ID, "test motivation")));

    // Assert
    assertEquals(ErrorCode.TUTOR_APPLICATION_ALREADY_PENDING, e.getCode());
  }

  @Test
  @DisplayName("A rejected application doesn't block a new one")
  void rejectedApplicationDoesNotBlock() {
    // Arrange - the student was rejected before, so no pending application exists
    Enrolment maths = passedEnrolment();
    Subject subject = maths.getSubjectEdition().getSubject();
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(1, 10, true));
    when(tutorApplicationRepository.existsByStudentNumberAndSubjectIdAndStatus(STUDENT_NUMBER, SUBJECT_ID,
        TutorApplicationStatus.PENDING)).thenReturn(false);
    when(subjectEditionService.findCurrentEdition(subject)).thenReturn(Optional.of(maths.getSubjectEdition()));
    when(tutorApplicationRepository.save(any(TutorApplication.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    TutorApplicationDTO result = tutorApplicationService.apply(student,
        new ApplyTutorRequestDTO(SUBJECT_ID, "test motivation"));

    // Assert
    assertEquals(TutorApplicationStatus.PENDING, result.status());
    verify(tutorApplicationRepository).save(any(TutorApplication.class));
  }

  @Test
  @DisplayName("A student who already tutors the subject can't apply again")
  void tutorRoleCannotApply() {
    // Arrange
    Enrolment maths = passedEnrolment();
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(1, 10, true));
    when(tutorRoleService.hasTutorRole(STUDENT_NUMBER, SUBJECT_ID)).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.apply(student, new ApplyTutorRequestDTO(SUBJECT_ID, "test motivation")));

    // Assert
    assertEquals(ErrorCode.TUTOR_ROLE_ALREADY_EXISTS, e.getCode());
  }

  @Test
  @DisplayName("A subject without a current edition can't be applied to")
  void noCurrentEditionCannotApply() {
    // Arrange
    Enrolment maths = passedEnrolment();
    Subject subject = maths.getSubjectEdition().getSubject();
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(1, 10, true));
    when(subjectEditionService.findCurrentEdition(subject)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.apply(student, new ApplyTutorRequestDTO(SUBJECT_ID, "test motivation")));

    // Assert
    assertEquals(ErrorCode.SUBJECT_NOT_TAUGHT_THIS_YEAR, e.getCode());
  }

  // approve tutor application (#81)

  private Teacher teacher() {
    Teacher teacher = ModelTestData.teacher();
    ReflectionTestUtils.setField(teacher, "number", 99L);
    return teacher;
  }

  // a subject the teacher teaches this year, with its current edition set up for checkCanDecide
  private Subject taughtSubject() {
    Subject subject = ModelTestData.subject();
    ReflectionTestUtils.setField(subject, "id", SUBJECT_ID);
    SubjectEdition edition = ModelTestData.subjectEdition();
    ReflectionTestUtils.setField(edition, "id", 77L);
    when(subjectEditionService.getCurrentEdition(subject)).thenReturn(edition);
    return subject;
  }

  @Test
  @DisplayName("The teacher lists the pending applications with grade, rank and previous ratings")
  void listsPendingApplications() {
    // Arrange
    Subject subject = taughtSubject();
    when(subjectService.getSubject(SUBJECT_ID)).thenReturn(subject);
    Enrolment maths = passedEnrolment();
    TutorApplication application = new TutorApplication(student, subject, "test motivation");
    ReflectionTestUtils.setField(application, "id", 7L);
    when(tutorApplicationRepository.findBySubjectIdAndStatusOrderBySubmittedAtAsc(SUBJECT_ID,
        TutorApplicationStatus.PENDING)).thenReturn(List.of(application));
    when(enrolmentService.getPassedEnrolment(student, SUBJECT_ID)).thenReturn(maths);
    when(enrolmentService.getTutorRanking(maths)).thenReturn(new TutorRankingDTO(2, 40, true));
    when(tutorRoleService.getTutorBadges(student)).thenReturn(List.of());

    // Act
    List<PendingTutorApplicationDTO> result = tutorApplicationService.listPendingApplications(99L, SUBJECT_ID);

    // Assert
    assertEquals(1, result.size());
    PendingTutorApplicationDTO item = result.getFirst();
    assertEquals(7L, item.applicationId());
    assertEquals(STUDENT_NUMBER, item.studentNumber());
    assertEquals(15, item.finalGrade());
    assertEquals(2, item.position());
    assertEquals("test motivation", item.motivation());
    assertTrue(item.previousRatings().isEmpty());
  }

  @Test
  @DisplayName("Approving creates the tutor role and sets the application to APPROVED")
  void approves() {
    // Arrange
    Subject subject = taughtSubject();
    TutorApplication application = new TutorApplication(student, subject, "test motivation");
    when(tutorApplicationRepository.findLockedById(7L)).thenReturn(Optional.of(application));
    when(tutorApplicationRepository.save(application)).thenReturn(application);
    when(tutorRoleService.save(any(TutorRole.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    TutorApplicationDTO result = tutorApplicationService.approve(teacher(), 7L);

    // Assert
    assertEquals(TutorApplicationStatus.APPROVED, result.status());
    verify(tutorRoleService).save(any(TutorRole.class));
  }

  @Test
  @DisplayName("Rejecting stores the reason and sets the application to REJECTED")
  void rejects() {
    // Arrange
    Subject subject = taughtSubject();
    TutorApplication application = new TutorApplication(student, subject, "test motivation");
    when(tutorApplicationRepository.findLockedById(7L)).thenReturn(Optional.of(application));
    when(tutorApplicationRepository.save(application)).thenReturn(application);

    // Act
    TutorApplicationDTO result = tutorApplicationService.reject(teacher(), 7L, "not good enough");

    // Assert
    assertEquals(TutorApplicationStatus.REJECTED, result.status());
    assertEquals("not good enough", application.getRejectionReason());
  }

  @Test
  @DisplayName("A teacher who doesn't teach the subject's current edition can't decide")
  void notTeacherCannotDecide() {
    // Arrange
    Subject subject = taughtSubject();
    TutorApplication application = new TutorApplication(student, subject, "test motivation");
    when(tutorApplicationRepository.findLockedById(7L)).thenReturn(Optional.of(application));
    doThrow(new StudyMatchException(ErrorCode.ACCESS_DENIED))
        .when(subjectEditionService).checkTaughtBy(77L, 99L);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.approve(teacher(), 7L));

    // Assert
    assertEquals(ErrorCode.ACCESS_DENIED, e.getCode());
    verify(tutorRoleService, never()).save(any());
  }

  @Test
  @DisplayName("An application that doesn't exist gives NOT_FOUND")
  void missingApplication() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.approve(teacher(), 7L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("A decided application can't be decided again")
  void alreadyDecidedCannotBeDecidedAgain() {
    // Arrange
    Subject subject = taughtSubject();
    TutorApplication application = new TutorApplication(student, subject, "test motivation");
    application.approve(ModelTestData.teacher());
    when(tutorApplicationRepository.findLockedById(7L)).thenReturn(Optional.of(application));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> tutorApplicationService.approve(teacher(), 7L));

    // Assert
    assertEquals(ErrorCode.APPLICATION_ALREADY_DECIDED, e.getCode());
  }

}
