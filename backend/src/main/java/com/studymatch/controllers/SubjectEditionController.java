package com.studymatch.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.studymatch.dtos.AssignmentResponseDTO;
import com.studymatch.dtos.AttendanceRequestDTO;
import com.studymatch.dtos.AttendanceSheetDTO;
import com.studymatch.dtos.ChangeClassRequestDTO;
import com.studymatch.dtos.CreateAssignmentRequestDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.EnrolStudentsRequestDTO;
import com.studymatch.dtos.EnrolmentRecordDTO;
import com.studymatch.dtos.EnrolmentResultDTO;
import com.studymatch.dtos.GradeRequestDTO;
import com.studymatch.dtos.GradeResponseDTO;
import com.studymatch.dtos.GradeSheetDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.services.AssignmentService;
import com.studymatch.services.EnrolmentService;
import com.studymatch.services.GradeService;
import com.studymatch.services.StudentService;
import com.studymatch.services.SubjectEditionService;
import com.studymatch.services.UserService;
import com.studymatch.utils.EnrolmentCsvReader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/subject-editions/")
@Tag(name = "Subject editions", description = "A subject in one academic year: enrolments, students, assignments, grades")
public class SubjectEditionController {

  private final SubjectEditionService subjectEditionService;
  private final EnrolmentService enrolmentService;
  private final UserService userService;
  private final AssignmentService assignmentService;
  private final StudentService studentService;
  private final EnrolmentCsvReader enrolmentCsvReader;
  private final GradeService gradeService;

  public SubjectEditionController(SubjectEditionService subjectEditionService, EnrolmentService enrolmentService,
      UserService userService, AssignmentService assignmentService, StudentService studentService,
      EnrolmentCsvReader enrolmentCsvReader, GradeService gradeService) {
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.userService = userService;
    this.assignmentService = assignmentService;
    this.studentService = studentService;
    this.enrolmentCsvReader = enrolmentCsvReader;
    this.gradeService = gradeService;
  }

  @GetMapping
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "The editions you teach, newest year first")
  public ResponseEntity<List<SubjectEditionResponseDTO>> listMyEditions(Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(subjectEditionService.listTaughtBy(teacher.getNumber()));
  }

  // enrol students (staff): the editions students can be enrolled in
  @GetMapping("current")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Editions of the current academic year (the newest registered), the ones open for enrolment")
  public ResponseEntity<List<SubjectEditionResponseDTO>> listCurrentEditions() {
    return ResponseEntity.ok(subjectEditionService.listCurrent());
  }

  // 200 and not 201, some students may just be skipped
  @PostMapping("{id}/enrolments")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Enrol students by number in one class, each number gets its own result")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SUBJECT_EDITION_NOT_CURRENT,
      ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION })
  public ResponseEntity<List<EnrolmentResultDTO>> enrolStudents(@PathVariable Long id,
      @Valid @RequestBody EnrolStudentsRequestDTO request) {
    Map<Long, Student> students = studentService.findByNumbers(request.studentNumbers());
    return ResponseEntity.ok(enrolmentService.enrolStudents(id, request.classSection(), request.studentNumbers(),
        students));
  }

  // change class: PUT since moving to the class the student is already in changes nothing
  @PutMapping("{id}/enrolments/{studentNumber}/class-section")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Move an enrolled student to another class of the same edition, keeping grades and absences")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SUBJECT_EDITION_NOT_CURRENT,
      ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, ErrorCode.STUDENT_NOT_ENROLLED })
  public ResponseEntity<EnrolmentRecordDTO> changeClass(@PathVariable Long id, @PathVariable Long studentNumber,
      @Valid @RequestBody ChangeClassRequestDTO request) {
    return ResponseEntity.ok(enrolmentService.changeClass(id, studentNumber, request.classSection()));
  }

  @GetMapping("enrolments/import/template")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Empty enrolments.csv with the columns the import expects")
  public ResponseEntity<String> enrolmentsTemplate() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"enrolments.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(enrolmentCsvReader.template());
  }

  // enrol students (CSV): one step, the valid rows are enrolled and every row gets its result
  @PostMapping(value = "{id}/enrolments/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Enrol the students of a CSV in one class, in one step. Each row gets its result")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SUBJECT_EDITION_NOT_CURRENT,
      ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, ErrorCode.CSV_EMPTY, ErrorCode.CSV_MISSING_COLUMNS,
      ErrorCode.CSV_TOO_MANY_ROWS, ErrorCode.CSV_WRONG_ENCODING, ErrorCode.CSV_INVALID, ErrorCode.FILE_TOO_LARGE })
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
  @Operation(summary = "Students of an edition you teach, with personality, category level and attendance")
  @ErrorCodes(ErrorCode.NOT_FOUND)
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
  @Operation(summary = "Add an assignment with its weight, and for group work the group sizes and separations")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_ALREADY_EXISTS, ErrorCode.ASSIGNMENT_WEIGHT_EXCEEDED,
      ErrorCode.GROUP_WORK_NOT_ALLOWED_FOR_EXAMS, ErrorCode.INVALID_GROUP_SIZES, ErrorCode.STUDENT_NOT_ENROLLED,
      ErrorCode.SEPARATION_SAME_STUDENT, ErrorCode.SEPARATION_ALREADY_EXISTS })
  public ResponseEntity<AssignmentResponseDTO> createAssignment(@PathVariable Long id,
      @Valid @RequestBody CreateAssignmentRequestDTO request, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.createAssignment(id, request));
  }

  // grade student: the sheet the teacher picks from
  @GetMapping("{id}/grades")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Grade sheet: assignments, students with their grades and final grade, and weightsComplete")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<GradeSheetDTO> getGradeSheet(@PathVariable Long id, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(gradeService.getGradeSheet(id));
  }

  // grade student: PUT since giving a grade again just replaces it
  @PutMapping("{id}/assignments/{assignmentId}/grades/{studentNumber}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Give or change a grade (0 to 20). Once every assignment is graded and weights reach 100%, "
      + "the final grade is set")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.STUDENT_NOT_ENROLLED })
  public ResponseEntity<GradeResponseDTO> gradeStudent(@PathVariable Long id, @PathVariable Long assignmentId,
      @PathVariable Long studentNumber, @Valid @RequestBody GradeRequestDTO request, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(gradeService.gradeStudent(id, assignmentId, studentNumber, request.value()));
  }

  // register attendance: the class's students for one lesson, with the record if it was registered before
  @GetMapping("{id}/attendance")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Attendance sheet of one lesson (class and start time): who was absent, justified or not, "
      + "and each student's attendance")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, ErrorCode.LESSON_IN_FUTURE })
  public ResponseEntity<AttendanceSheetDTO> getAttendanceSheet(@PathVariable Long id,
      @RequestParam String classSection,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt) {
    return ResponseEntity.ok(enrolmentService.getAttendanceSheet(id, classSection, startsAt));
  }

  // register attendance: PUT since saving the same lesson again replaces its record
  @PutMapping("{id}/attendance")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Register or correct a lesson's attendance. Students not listed as absent were present")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SUBJECT_EDITION_NOT_CURRENT,
      ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, ErrorCode.LESSON_IN_FUTURE, ErrorCode.STUDENT_NOT_ENROLLED })
  public ResponseEntity<AttendanceSheetDTO> registerAttendance(@PathVariable Long id,
      @Valid @RequestBody AttendanceRequestDTO request) {
    return ResponseEntity.ok(enrolmentService.registerAttendance(id, request));
  }

  // delete a lesson: same way of picking it as the sheet
  @DeleteMapping("{id}/attendance")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Delete a lesson registered by mistake, with its absences, so it stops counting as held")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SUBJECT_EDITION_NOT_CURRENT,
      ErrorCode.CLASS_SECTION_NOT_IN_SUBJECT_EDITION, ErrorCode.LESSON_IN_FUTURE })
  public ResponseEntity<Void> deleteLesson(@PathVariable Long id, @RequestParam String classSection,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt) {
    enrolmentService.deleteLesson(id, classSection, startsAt);
    return ResponseEntity.noContent().build();
  }

}
