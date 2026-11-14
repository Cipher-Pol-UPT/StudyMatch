package com.studymatch.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.GradeResponseDTO;
import com.studymatch.dtos.GradeSheetDTO;
import com.studymatch.models.Assignment;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Grade;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.GradeRepository;

@Service
public class GradeService {

  private final GradeRepository gradeRepository;
  private final SubjectEditionService subjectEditionService;
  private final AssignmentService assignmentService;
  private final EnrolmentService enrolmentService;

  public GradeService(GradeRepository gradeRepository, SubjectEditionService subjectEditionService,
      AssignmentService assignmentService, EnrolmentService enrolmentService) {
    this.gradeRepository = gradeRepository;
    this.subjectEditionService = subjectEditionService;
    this.assignmentService = assignmentService;
    this.enrolmentService = enrolmentService;
  }

  // grade sheet: all the edition's grades in one query instead of one per student
  @Transactional(readOnly = true)
  public GradeSheetDTO getGradeSheet(Long subjectEditionId) {
    SubjectEdition edition = subjectEditionService.getEdition(subjectEditionId);
    Map<Long, List<Grade>> gradesByEnrolment = gradeRepository.findByEnrolment_SubjectEditionId(subjectEditionId)
        .stream()
        .collect(Collectors.groupingBy(grade -> grade.getEnrolment().getId()));

    List<GradeSheetDTO.Column> columns = assignmentService.listAssignments(edition).stream()
        .map(GradeSheetDTO.Column::from)
        .toList();
    List<GradeSheetDTO.Row> rows = enrolmentService.listEnrolments(subjectEditionId).stream()
        .map(enrolment -> GradeSheetDTO.Row.from(enrolment,
            gradesByEnrolment.getOrDefault(enrolment.getId(), List.of())))
        .toList();
    return new GradeSheetDTO(columns, rows, assignmentService.hasFullWeight(edition));
  }

  // grade student: the first grade and a change are the same call
  @Transactional
  public GradeResponseDTO gradeStudent(Long subjectEditionId, Long assignmentId, Long studentNumber,
      BigDecimal value) {
    // locked so two grades for the same student at once can't both miss the final grade
    SubjectEdition edition = subjectEditionService.getLockedEdition(subjectEditionId);
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    Enrolment enrolment = enrolmentService.getEnrolment(subjectEditionId, studentNumber);

    Grade grade = gradeRepository.findByEnrolmentAndAssignment(enrolment, assignment)
        .orElseGet(() -> new Grade(enrolment, assignment, value.doubleValue()));
    grade.setValue(value.doubleValue());
    gradeRepository.save(grade);

    // a student can only have one grade per assignment, so same count = all graded
    List<Grade> grades = gradeRepository.findByEnrolment(enrolment);
    boolean weightsComplete = assignmentService.hasFullWeight(edition);
    if (weightsComplete && grades.size() == assignmentService.listAssignments(edition).size()) {
      enrolment.setFinalGrade(finalGrade(grades));
    }
    return new GradeResponseDTO(GradeSheetDTO.Row.from(enrolment, grades), weightsComplete);
  }

  // GR2: weighted average rounded half up, so 9.5 passes
  private static int finalGrade(List<Grade> grades) {
    return grades.stream()
        .map(grade -> BigDecimal.valueOf(grade.getValue()).multiply(grade.getAssignment().getWeight()))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

}
