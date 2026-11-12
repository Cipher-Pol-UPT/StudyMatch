package com.studymatch.services;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AssignmentResponseDTO;
import com.studymatch.dtos.CreateAssignmentRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Assignment;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.AssignmentRepository;

@Service
public class AssignmentService {

  private final AssignmentRepository assignmentRepository;
  private final SubjectEditionService subjectEditionService;
  private final EnrolmentService enrolmentService;

  public AssignmentService(AssignmentRepository assignmentRepository, SubjectEditionService subjectEditionService,
      EnrolmentService enrolmentService) {
    this.assignmentRepository = assignmentRepository;
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
  }

  // sizes and "only projects can be group work" are refused by the models themselves
  @Transactional
  public AssignmentResponseDTO createAssignment(Long subjectEditionId, CreateAssignmentRequestDTO request) {
    // locked so two teachers adding at the same time can't both take the same name or remaining weight
    SubjectEdition edition = subjectEditionService.getLockedEdition(subjectEditionId);
    String name = request.name().trim();
    if (assignmentRepository.existsBySubjectEditionAndNameIgnoreCase(edition, name)) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
    }
    BigDecimal remaining = BigDecimal.ONE.subtract(usedWeight(edition));
    if (request.weightFraction().compareTo(remaining) > 0) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_WEIGHT_EXCEEDED,
          Map.of("remaining", remaining.movePointRight(2).intValue()));
    }

    CreateAssignmentRequestDTO.Grouping grouping = request.grouping();
    GroupingRule rule = grouping == null ? null
        : new GroupingRule(grouping.minSize(), grouping.optimalSize(), grouping.maxSize(), grouping.sameClass());
    Assignment assignment = new Assignment(edition, name, request.type(), request.date(),
        request.weightFraction(), rule);

    // after the assignment so an exam with grouping fails before looking up any student
    if (grouping != null) {
      for (CreateAssignmentRequestDTO.Separation pair : grouping.separationsOrEmpty()) {
        rule.addSeparation(enrolmentService.getEnrolledStudent(subjectEditionId, pair.firstStudent()),
            enrolmentService.getEnrolledStudent(subjectEditionId, pair.secondStudent()));
      }
    }
    return AssignmentResponseDTO.from(assignmentRepository.save(assignment));
  }

  private BigDecimal usedWeight(SubjectEdition edition) {
    return assignmentRepository.findBySubjectEdition(edition).stream()
        .map(Assignment::getWeight)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

}
