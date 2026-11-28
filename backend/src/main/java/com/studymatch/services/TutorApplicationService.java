package com.studymatch.services;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ApplyTutorRequestDTO;
import com.studymatch.dtos.PendingTutorApplicationDTO;
import com.studymatch.dtos.TutorApplicationDTO;
import com.studymatch.dtos.TutorEligibleSubjectDTO;
import com.studymatch.dtos.TutorRankingDTO;
import com.studymatch.dtos.TutorSubjectDTO;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorApplication;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.TutorApplicationRepository;

@Service
public class TutorApplicationService {

  private final TutorApplicationRepository tutorApplicationRepository;
  private final EnrolmentService enrolmentService;
  private final TutorRoleService tutorRoleService;
  private final SubjectEditionService subjectEditionService;
  private final SubjectService subjectService;
  private final NotificationService notificationService;

  public TutorApplicationService(TutorApplicationRepository tutorApplicationRepository,
      EnrolmentService enrolmentService, TutorRoleService tutorRoleService,
      SubjectEditionService subjectEditionService, SubjectService subjectService,
      NotificationService notificationService) {
    this.tutorApplicationRepository = tutorApplicationRepository;
    this.enrolmentService = enrolmentService;
    this.tutorRoleService = tutorRoleService;
    this.subjectEditionService = subjectEditionService;
    this.subjectService = subjectService;
    this.notificationService = notificationService;
  }

  public int countPending(Long studentNumber) {
    return findPending(studentNumber).size();
  }

  // block account: nobody can decide them anymore
  @Transactional
  public int cancelPending(Long studentNumber) {
    List<TutorApplication> applications = findPending(studentNumber);
    applications.forEach(TutorApplication::cancel);
    tutorApplicationRepository.saveAll(applications);
    return applications.size();
  }

  private List<TutorApplication> findPending(Long studentNumber) {
    return tutorApplicationRepository.findByStudentNumberAndStatus(studentNumber, TutorApplicationStatus.PENDING);
  }

  // apply for tutoring (#41): every subject the student passed, with their rank and whether they can apply
  @Transactional(readOnly = true)
  public List<TutorEligibleSubjectDTO> listApplicableSubjects(Student student) {
    List<Enrolment> passed = enrolmentService.getPassedEnrolments(student);

    // loaded once, not once per subject: the blocking states and the current year
    Set<Long> pendingSubjects = tutorApplicationRepository
        .findByStudentNumberAndStatus(student.getNumber(), TutorApplicationStatus.PENDING).stream()
        .map(application -> application.getSubject().getId())
        .collect(Collectors.toSet());
    Set<Long> tutoredSubjects = tutorRoleService.getTutoredSubjects(student.getNumber()).stream()
        .map(TutorSubjectDTO::subjectId)
        .collect(Collectors.toSet());
    Optional<AcademicYear> currentYear = subjectEditionService.findCurrentYear();

    return passed.stream()
        .map(enrolment -> toEligibleSubject(enrolment, pendingSubjects, tutoredSubjects, currentYear))
        .sorted(Comparator.comparing(TutorEligibleSubjectDTO::subjectName))
        .toList();
  }

  // apply for tutoring (#41): the student asks to tutor a subject they passed, in the top 5% of their edition
  @Transactional
  public TutorApplicationDTO apply(Student student, ApplyTutorRequestDTO request) {
    Enrolment enrolment = enrolmentService.getPassedEnrolment(student, request.subjectId());
    Subject subject = enrolment.getSubjectEdition().getSubject();

    if (!enrolmentService.getTutorRanking(enrolment).eligible()) {
      throw new StudyMatchException(ErrorCode.NOT_ELIGIBLE_FOR_TUTORING);
    }
    if (hasPendingApplication(student.getNumber(), subject.getId())) {
      throw new StudyMatchException(ErrorCode.TUTOR_APPLICATION_ALREADY_PENDING);
    }
    if (tutorRoleService.hasTutorRole(student.getNumber(), subject.getId())) {
      throw new StudyMatchException(ErrorCode.TUTOR_ROLE_ALREADY_EXISTS);
    }
    Optional<SubjectEdition> edition = subjectEditionService.findCurrentEdition(subject);
    if (edition.isEmpty()) {
      throw new StudyMatchException(ErrorCode.SUBJECT_NOT_TAUGHT_THIS_YEAR);
    }

    TutorApplication application = tutorApplicationRepository
        .save(new TutorApplication(student, subject, request.motivation()));
    // every teacher of the current edition can decide it, so they're all told
    notificationService.notifyAll(edition.get().getTeachers(), NotificationType.TUTOR_APPLICATION_SUBMITTED,
        NotificationTarget.TUTOR_APPLICATION, application.getId(), Map.of(
            "subject", subject.getName(),
            "subjectId", subject.getId(),
            "student", student.getName(),
            "number", student.getNumber()));
    return TutorApplicationDTO.from(application);
  }

  // approve tutor application (#81): the pending applications for a subject, with the applicant's grade, rank and ratings
  @Transactional(readOnly = true)
  public List<PendingTutorApplicationDTO> listPendingApplications(Long teacherNumber, Long subjectId) {
    checkCanDecide(subjectService.getSubject(subjectId), teacherNumber);
    return tutorApplicationRepository
        .findBySubjectIdAndStatusOrderBySubmittedAtAsc(subjectId, TutorApplicationStatus.PENDING).stream()
        .map(application -> toPendingApplication(application, subjectId))
        .toList();
  }

  // approve tutor application (#81): the student becomes the subject's tutor
  @Transactional
  public TutorApplicationDTO approve(Teacher teacher, Long applicationId) {
    TutorApplication application = getApplication(applicationId);
    checkCanDecide(application.getSubject(), teacher.getNumber());

    TutorRole role = application.approve(teacher);
    tutorRoleService.save(role);
    notifyDecision(application, NotificationType.TUTOR_APPLICATION_APPROVED);
    return TutorApplicationDTO.from(tutorApplicationRepository.save(application));
  }

  // approve tutor application (#81): reject with an optional reason
  @Transactional
  public TutorApplicationDTO reject(Teacher teacher, Long applicationId, String reason) {
    TutorApplication application = getApplication(applicationId);
    checkCanDecide(application.getSubject(), teacher.getNumber());

    application.reject(teacher, reason);
    notifyDecision(application, NotificationType.TUTOR_APPLICATION_REJECTED);
    return TutorApplicationDTO.from(tutorApplicationRepository.save(application));
  }

  // the reason only when the teacher gave one
  private void notifyDecision(TutorApplication application, NotificationType type) {
    Subject subject = application.getSubject();
    Map<String, Object> params = new HashMap<>();
    params.put("subject", subject.getName());
    params.put("subjectId", subject.getId());
    if (application.getRejectionReason() != null) {
      params.put("reason", application.getRejectionReason());
    }
    notificationService.notify(application.getStudent(), type, NotificationTarget.TUTOR_APPLICATION,
        application.getId(), params);
  }

  // only the teacher of the subject's current edition decides
  private void checkCanDecide(Subject subject, Long teacherNumber) {
    SubjectEdition edition = subjectEditionService.getCurrentEdition(subject);
    subjectEditionService.checkTaughtBy(edition.getId(), teacherNumber);
  }

  private TutorApplication getApplication(Long applicationId) {
    // locked so two teachers can't both decide the same application as PENDING
    return tutorApplicationRepository.findLockedById(applicationId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  private PendingTutorApplicationDTO toPendingApplication(TutorApplication application, Long subjectId) {
    Student student = application.getStudent();
    Enrolment enrolment = enrolmentService.getPassedEnrolment(student, subjectId);
    TutorRankingDTO ranking = enrolmentService.getTutorRanking(enrolment);
    return new PendingTutorApplicationDTO(application.getId(), student.getNumber(), student.getName(),
        enrolment.getFinalGrade(), ranking.position(), application.getMotivation(),
        tutorRoleService.getTutorBadges(student), application.getSubmittedAt());
  }

  private TutorEligibleSubjectDTO toEligibleSubject(Enrolment enrolment, Set<Long> pendingSubjects,
      Set<Long> tutoredSubjects, Optional<AcademicYear> currentYear) {
    Subject subject = enrolment.getSubjectEdition().getSubject();
    Long subjectId = subject.getId();
    TutorRankingDTO ranking = enrolmentService.getTutorRanking(enrolment);
    boolean hasCurrentEdition = currentYear.map(year -> subjectEditionService.hasEdition(subject, year)).orElse(false);
    return new TutorEligibleSubjectDTO(subjectId, subject.getName(), enrolment.getFinalGrade(),
        ranking.position(), ranking.editionSize(), ranking.eligible(),
        pendingSubjects.contains(subjectId), tutoredSubjects.contains(subjectId), hasCurrentEdition);
  }

  private boolean hasPendingApplication(Long studentNumber, Long subjectId) {
    return tutorApplicationRepository.existsByStudentNumberAndSubjectIdAndStatus(studentNumber, subjectId,
        TutorApplicationStatus.PENDING);
  }

}
