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

import com.studymatch.dtos.AssignmentGroupsDTO;
import com.studymatch.dtos.AssignmentResponseDTO;
import com.studymatch.dtos.AttendanceRequestDTO;
import com.studymatch.dtos.AttendanceSheetDTO;
import com.studymatch.dtos.ChangeClassRequestDTO;
import com.studymatch.dtos.CreateAssignmentRequestDTO;
import com.studymatch.dtos.EditGroupsRequestDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.EnrolStudentsRequestDTO;
import com.studymatch.dtos.EnrolmentRecordDTO;
import com.studymatch.dtos.EnrolmentResultDTO;
import com.studymatch.dtos.GradeRequestDTO;
import com.studymatch.dtos.GradeResponseDTO;
import com.studymatch.dtos.GradeSheetDTO;
import com.studymatch.dtos.GroupDTO;
import com.studymatch.dtos.GroupGenerationDTO;
import com.studymatch.dtos.GroupImportDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.enums.ExportFormat;
import com.studymatch.enums.GroupStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.services.AssignmentService;
import com.studymatch.services.EnrolmentService;
import com.studymatch.services.GradeService;
import com.studymatch.services.GroupService;
import com.studymatch.services.StudentService;
import com.studymatch.services.SubjectEditionService;
import com.studymatch.services.UserService;
import com.studymatch.utils.EnrolmentCsvReader;
import com.studymatch.utils.GroupCsvReader;

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
  private final GroupCsvReader groupCsvReader;
  private final GradeService gradeService;
  private final GroupService groupService;

  public SubjectEditionController(SubjectEditionService subjectEditionService, EnrolmentService enrolmentService,
      UserService userService, AssignmentService assignmentService, StudentService studentService,
      EnrolmentCsvReader enrolmentCsvReader, GroupCsvReader groupCsvReader, GradeService gradeService,
      GroupService groupService) {
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.userService = userService;
    this.assignmentService = assignmentService;
    this.studentService = studentService;
    this.enrolmentCsvReader = enrolmentCsvReader;
    this.groupCsvReader = groupCsvReader;
    this.gradeService = gradeService;
    this.groupService = groupService;
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

  // view groups
  @GetMapping("{id}/assignments/{assignmentId}/groups")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "The groups of a group assignment, the drafts and the published ones, with their members. "
      + "Students with a statute show only that attendance doesn't apply, never the statute type (GR7)")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK })
  public ResponseEntity<AssignmentGroupsDTO> listGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(groupService.listGroups(id, assignmentId));
  }

  // export groups: the language only changes the PDF's headings, the CSV's columns stay what Import Groups reads
  @GetMapping("{id}/assignments/{assignmentId}/groups/export")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Download the published groups as CSV, one row per student with groupNumber, studentNumber, "
      + "name and institutionalEmail (the columns Import Groups reads), or as a PDF table per group. "
      + "Never the statute (GR7)")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, ErrorCode.NO_GROUPS_TO_EXPORT })
  public ResponseEntity<byte[]> exportGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      @RequestParam ExportFormat format, @RequestParam(defaultValue = "en") String language,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    byte[] file = groupService.exportGroups(id, assignmentId, format, language);
    boolean csv = format == ExportFormat.CSV;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"groups." + (csv ? "csv" : "pdf") + "\"")
        .contentType(csv ? new MediaType("text", "csv", StandardCharsets.UTF_8) : MediaType.APPLICATION_PDF)
        .body(file);
  }

  @GetMapping("groups/import/template")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Empty groups.csv with the export's columns, only groupNumber and studentNumber are read")
  public ResponseEntity<String> groupsTemplate() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"groups.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(groupCsvReader.template());
  }

  // import groups: previews them as drafts, confirming and cancelling are generate's publish and discard
  @PostMapping(value = "{id}/assignments/{assignmentId}/groups/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Import the groups of a CSV as drafts and preview them. Students not enrolled or blocked are "
      + "skipped, the enrolled ones the file leaves out form leftover groups. An invalid row blocks the whole file. "
      + "With published groups already, replacePublished=true confirms they'll be replaced once these are confirmed")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, ErrorCode.GROUPS_ALREADY_PUBLISHED,
      ErrorCode.GROUP_IMPORT_INVALID_ROWS, ErrorCode.GROUP_FILE_NOT_THIS_EDITION,
      ErrorCode.NOT_ENOUGH_STUDENTS_FOR_GROUPS, ErrorCode.CSV_EMPTY, ErrorCode.CSV_MISSING_COLUMNS,
      ErrorCode.CSV_TOO_MANY_ROWS, ErrorCode.CSV_WRONG_ENCODING, ErrorCode.CSV_INVALID, ErrorCode.FILE_TOO_LARGE })
  public ResponseEntity<GroupImportDTO> importGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      @RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "false") boolean replacePublished,
      Authentication authentication) throws IOException {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    List<GroupCsvReader.Row> rows = groupCsvReader.read(file.getBytes());
    Map<Long, Student> students = studentService.findByNumbers(rows.stream()
        .map(GroupCsvReader.Row::studentNumber)
        .filter(Objects::nonNull)
        .toList());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(groupService.importGroups(id, assignmentId, rows, students.keySet(), replacePublished));
  }

  // generate groups: regenerating is the same call, the new drafts replace the old ones
  @PostMapping("{id}/assignments/{assignmentId}/groups")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Generate draft groups for a group assignment and preview them. With published groups already, "
      + "replacePublished=true confirms they'll be replaced once the new ones are confirmed")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK, ErrorCode.GROUPS_ALREADY_PUBLISHED,
      ErrorCode.NOT_ENOUGH_STUDENTS_FOR_GROUPS, ErrorCode.NO_VALID_GROUP_SPLIT, ErrorCode.SEPARATIONS_CANNOT_BE_MET })
  public ResponseEntity<List<GroupDTO>> generateGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      @RequestParam(defaultValue = "false") boolean replacePublished, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(groupService.generateGroups(id, assignmentId, replacePublished));
  }

  // generate groups, before generating: the page's loader follows the real search time
  @GetMapping("{id}/assignments/{assignmentId}/groups/generation")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "How many groups generating will form, their optimal size and how long the matching algorithm "
      + "searches, so the page can show its progress")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK })
  public ResponseEntity<GroupGenerationDTO> planGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(groupService.planGroups(id, assignmentId));
  }

  // generate groups, confirm
  @PostMapping("{id}/assignments/{assignmentId}/groups/publish")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Publish the draft groups, replacing the ones published before")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NO_DRAFT_GROUPS })
  public ResponseEntity<Void> publishGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    groupService.publishGroups(id, assignmentId);
    return ResponseEntity.noContent().build();
  }

  // generate groups, cancel
  @DeleteMapping("{id}/assignments/{assignmentId}/groups/draft")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Discard the draft groups, the published ones stay as they are")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<Void> discardGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    groupService.discardGroups(id, assignmentId);
    return ResponseEntity.noContent().build();
  }

  // edit groups, from the generate preview
  @PutMapping("{id}/assignments/{assignmentId}/groups/draft")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Save the edited draft groups: every group after the edit, a group left out is removed and "
      + "an id of null is a new one. Sizes and constraints only warn on the page, but every enrolled student who "
      + "isn't blocked has to be in exactly one group")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.VALIDATION_FAILED, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK,
      ErrorCode.NO_DRAFT_GROUPS, ErrorCode.STUDENT_NOT_ENROLLED, ErrorCode.STUDENT_IN_TWO_GROUPS,
      ErrorCode.STUDENT_WITHOUT_GROUP, ErrorCode.BLOCKED_STUDENT_MOVED })
  public ResponseEntity<List<GroupDTO>> editDraftGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      @Valid @RequestBody EditGroupsRequestDTO request, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(groupService.editGroups(id, assignmentId, GroupStatus.DRAFT, request.groups()));
  }

  // edit groups, after publishing
  @PutMapping("{id}/assignments/{assignmentId}/groups/published")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Save the edited published groups, the same way as the drafts. A kept group stays the same "
      + "group, a new one is published straight away")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.VALIDATION_FAILED, ErrorCode.ASSIGNMENT_NOT_GROUP_WORK,
      ErrorCode.NO_PUBLISHED_GROUPS, ErrorCode.STUDENT_NOT_ENROLLED, ErrorCode.STUDENT_IN_TWO_GROUPS,
      ErrorCode.STUDENT_WITHOUT_GROUP, ErrorCode.BLOCKED_STUDENT_MOVED })
  public ResponseEntity<List<GroupDTO>> editPublishedGroups(@PathVariable Long id, @PathVariable Long assignmentId,
      @Valid @RequestBody EditGroupsRequestDTO request, Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    subjectEditionService.checkTaughtBy(id, teacher.getNumber());
    return ResponseEntity.ok(groupService.editGroups(id, assignmentId, GroupStatus.PUBLISHED, request.groups()));
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
