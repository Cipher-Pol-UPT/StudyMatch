package com.studymatch.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
import com.studymatch.utils.EnrolmentCsvReader;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subject-editions/")
public class SubjectEditionController {

  private final SubjectEditionService subjectEditionService;
  private final EnrolmentService enrolmentService;
  private final UserService userService;
  private final AssignmentService assignmentService;
  private final StudentService studentService;
  private final EnrolmentCsvReader enrolmentCsvReader;

  public SubjectEditionController(SubjectEditionService subjectEditionService, EnrolmentService enrolmentService,
      UserService userService, AssignmentService assignmentService, StudentService studentService,
      EnrolmentCsvReader enrolmentCsvReader) {
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.userService = userService;
    this.assignmentService = assignmentService;
    this.studentService = studentService;
    this.enrolmentCsvReader = enrolmentCsvReader;
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

  @GetMapping("enrolments/import/template")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<String> enrolmentsTemplate() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enrolments.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(enrolmentCsvReader.template());
  }

  // enrol students (CSV): one step, the valid rows are enrolled and every row gets its result
  @PostMapping(value = "{id}/enrolments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<List<EnrolmentResultDTO>> importEnrolments(@PathVariable Long id,
      @RequestPart("file") MultipartFile file, @RequestParam String classSection) throws IOException {
    List<EnrolmentCsvReader.Row> rows = enrolmentCsvReader.read(file.getBytes());
    Map<Long, Student> students = studentService.findByNumbers(rows.stream()
        .map(EnrolmentCsvReader.Row::number)
        .filter(Objects::nonNull)
        .toList());
    return ResponseEntity.ok(enrolmentService.importEnrolments(id, classSection, rows, students));
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
