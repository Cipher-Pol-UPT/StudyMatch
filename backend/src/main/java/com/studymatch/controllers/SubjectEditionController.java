package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.AssignmentResponseDTO;
import com.studymatch.dtos.CreateAssignmentRequestDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.GradeRequestDTO;
import com.studymatch.dtos.GradeResponseDTO;
import com.studymatch.dtos.GradeSheetDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.models.User;
import com.studymatch.services.AssignmentService;
import com.studymatch.services.EnrolmentService;
import com.studymatch.services.GradeService;
import com.studymatch.services.SubjectEditionService;
import com.studymatch.services.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subject-editions/")
public class SubjectEditionController {

  private final SubjectEditionService subjectEditionService;
  private final EnrolmentService enrolmentService;
  private final UserService userService;
  private final AssignmentService assignmentService;
  private final GradeService gradeService;

  public SubjectEditionController(SubjectEditionService subjectEditionService, EnrolmentService enrolmentService,
      UserService userService, AssignmentService assignmentService, GradeService gradeService) {
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.userService = userService;
    this.assignmentService = assignmentService;
    this.gradeService = gradeService;
  }

  @GetMapping
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<List<SubjectEditionResponseDTO>> listMyEditions(Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(subjectEditionService.listTaughtBy(teacher.getNumber()));
  }

  // view students (teacher): only for teachers of this edition
  @GetMapping("{id}/students")
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<List<EditionStudentDTO>> listStudents(@PathVariable Long id,
      @RequestParam(required = false) String search, @RequestParam(required = false) String classSection,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(enrolmentService.listStudentsOfEdition(id, search, classSection));
  }

  // create assignment: only for teachers of this edition
  @PostMapping("{id}/assignments")
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<AssignmentResponseDTO> createAssignment(@PathVariable Long id,
      @Valid @RequestBody CreateAssignmentRequestDTO request, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.createAssignment(id, request));
  }

  // grade student: the sheet the teacher picks from
  @GetMapping("{id}/grades")
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<GradeSheetDTO> getGradeSheet(@PathVariable Long id, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(gradeService.getGradeSheet(id));
  }

  // grade student: PUT since giving a grade again just replaces it
  @PutMapping("{id}/assignments/{assignmentId}/grades/{studentNumber}")
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<GradeResponseDTO> gradeStudent(@PathVariable Long id, @PathVariable Long assignmentId,
      @PathVariable Long studentNumber, @Valid @RequestBody GradeRequestDTO request, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(gradeService.gradeStudent(id, assignmentId, studentNumber, request.value()));
  }

}
