package com.studymatch.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.AssignmentResponseDTO;
import com.studymatch.dtos.CreateAssignmentRequestDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.EnrolStudentsRequestDTO;
import com.studymatch.dtos.EnrolmentResultDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.services.AssignmentService;
import com.studymatch.services.EnrolmentService;
import com.studymatch.services.StudentService;
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
  private final StudentService studentService;

  public SubjectEditionController(SubjectEditionService subjectEditionService, EnrolmentService enrolmentService,
      UserService userService, AssignmentService assignmentService, StudentService studentService) {
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.userService = userService;
    this.assignmentService = assignmentService;
    this.studentService = studentService;
  }

  @GetMapping
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<List<SubjectEditionResponseDTO>> listMyEditions(Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(subjectEditionService.listTaughtBy(teacher.getNumber()));
  }

  // enrol students (staff): the editions students can be enrolled in
  @GetMapping("current")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<List<SubjectEditionResponseDTO>> listCurrentEditions() {
    return ResponseEntity.ok(subjectEditionService.listCurrent());
  }

  // 200 and not 201, some students may just be skipped
  @PostMapping("{id}/enrolments")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<List<EnrolmentResultDTO>> enrolStudents(@PathVariable Long id,
      @Valid @RequestBody EnrolStudentsRequestDTO request) {
    Map<Long, Student> students = studentService.findByNumbers(request.studentNumbers());
    return ResponseEntity.ok(enrolmentService.enrolStudents(id, request.classSection(), request.studentNumbers(),
        students));
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

}
