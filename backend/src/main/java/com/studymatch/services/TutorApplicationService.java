package com.studymatch.services;

import java.util.Comparator;
import java.util.List;
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

  public TutorApplicationService(TutorApplicationRepository tutorApplicationRepository,
      EnrolmentService enrolmentService, TutorRoleService tutorRoleService,
      SubjectEditionService subjectEditionService, SubjectService subjectService) {
    this.tutorApplicationRepository = tutorApplicationRepository;
    this.enrolmentService = enrolmentService;
    this.tutorRoleService = tutorRoleService;
    this.subjectEditionService = subjectEditionService;
    this.subjectService = subjectService;
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
    if (subjectEditionService.findCurrentEdition(subject).isEmpty()) {
      throw new StudyMatchException(ErrorCode.SUBJECT_NOT_TAUGHT_THIS_YEAR);
    }

    // notifying the subject's teacher comes with notifications (#144)
    TutorApplication application = new TutorApplication(student, subject, request.motivation());
    return TutorApplicationDTO.from(tutorApplicationRepository.save(application));
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
    // notifying the student comes with notifications (#144)
    return TutorApplicationDTO.from(tutorApplicationRepository.save(application));
  }

  // approve tutor application (#81): reject with an optional reason
  @Transactional
  public TutorApplicationDTO reject(Teacher teacher, Long applicationId, String reason) {
    TutorApplication application = getApplication(applicationId);
    checkCanDecide(application.getSubject(), teacher.getNumber());

    application.reject(teacher, reason);
    // notifying the student, with the reason if given, comes with notifications (#144)
    return TutorApplicationDTO.from(tutorApplicationRepository.save(application));
  }

  // only the teacher of the subject's current edition decides
  private void checkCanDecide(Subject subject, Long teacherNumber) {
    SubjectEdition edition = subjectEditionService.getCurrentEdition(subject);
    subjectEditionService.checkTaughtBy(edition.getId(), teacherNumber);
  }

  private TutorApplication getApplication(Long applicationId) {
    return tutorApplicationRepository.findById(applicationId)
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
