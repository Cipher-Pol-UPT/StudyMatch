package com.studymatch.services;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ApplyTutorRequestDTO;
import com.studymatch.dtos.TutorApplicationDTO;
import com.studymatch.dtos.TutorEligibleSubjectDTO;
import com.studymatch.dtos.TutorRankingDTO;
import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.TutorApplication;
import com.studymatch.repositories.TutorApplicationRepository;

@Service
public class TutorApplicationService {

  private final TutorApplicationRepository tutorApplicationRepository;
  private final EnrolmentService enrolmentService;
  private final TutorRoleService tutorRoleService;
  private final SubjectEditionService subjectEditionService;

  public TutorApplicationService(TutorApplicationRepository tutorApplicationRepository,
      EnrolmentService enrolmentService, TutorRoleService tutorRoleService,
      SubjectEditionService subjectEditionService) {
    this.tutorApplicationRepository = tutorApplicationRepository;
    this.enrolmentService = enrolmentService;
    this.tutorRoleService = tutorRoleService;
    this.subjectEditionService = subjectEditionService;
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
    return enrolmentService.getPassedEnrolments(student).stream()
        .map(enrolment -> toEligibleSubject(student, enrolment))
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

  private TutorEligibleSubjectDTO toEligibleSubject(Student student, Enrolment enrolment) {
    Subject subject = enrolment.getSubjectEdition().getSubject();
    Long subjectId = subject.getId();
    TutorRankingDTO ranking = enrolmentService.getTutorRanking(enrolment);
    return new TutorEligibleSubjectDTO(subjectId, subject.getName(), enrolment.getFinalGrade(),
        ranking.position(), ranking.editionSize(), ranking.eligible(),
        hasPendingApplication(student.getNumber(), subjectId),
        tutorRoleService.hasTutorRole(student.getNumber(), subjectId),
        subjectEditionService.findCurrentEdition(subject).isPresent());
  }

  private boolean hasPendingApplication(Long studentNumber, Long subjectId) {
    return tutorApplicationRepository.findByStudentNumberAndSubjectId(studentNumber, subjectId)
        .map(application -> application.getStatus() == TutorApplicationStatus.PENDING)
        .orElse(false);
  }

}
