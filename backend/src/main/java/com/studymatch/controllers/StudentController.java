package com.studymatch.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
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

import com.studymatch.dtos.EditStudentRequestDTO;
import com.studymatch.dtos.ImageFileDTO;
import com.studymatch.dtos.ImportPreviewRowDTO;
import com.studymatch.dtos.ImportResultRowDTO;
import com.studymatch.dtos.ImportStudentsRequestDTO;
import com.studymatch.dtos.MyGroupDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.dtos.PersonalityTestRequestDTO;
import com.studymatch.dtos.RatingDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;
import com.studymatch.dtos.RegisteredStudentDTO;
import com.studymatch.dtos.ResendCredentialsRequestDTO;
import com.studymatch.dtos.StatuteRequestDTO;
import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.StudentRecordDTO;
import com.studymatch.dtos.StudentSummaryDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.AccountStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
import com.studymatch.services.GroupService;
import com.studymatch.services.RatingService;
import com.studymatch.services.StudentService;
import com.studymatch.services.UserService;
import com.studymatch.utils.StudentCsvReader;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/students/")
@Tag(name = "Students", description = "Registering and editing students (staff), profiles and the personality test")
public class StudentController {

  private final StudentService studentService;
  private final RatingService ratingService;
  private final GroupService groupService;
  private final UserService userService;
  private final StudentCsvReader csvReader;

  public StudentController(StudentService studentService, RatingService ratingService, GroupService groupService,
      UserService userService, StudentCsvReader csvReader) {
    this.studentService = studentService;
    this.ratingService = ratingService;
    this.groupService = groupService;
    this.userService = userService;
    this.csvReader = csvReader;
  }

  // view students (staff)

  @GetMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Search students by name or number, 20 per page, every filter optional")
  public ResponseEntity<PageDTO<StudentSummaryDTO>> listStudents(@RequestParam(required = false) String search,
      @RequestParam(required = false) Long degreeId, @RequestParam(required = false) Integer curricularYear,
      @RequestParam(required = false) AccountStatus status, @RequestParam(defaultValue = "0") int page) {
    return ResponseEntity.ok(studentService.listStudents(search, degreeId, curricularYear, status, page));
  }

  @GetMapping("{number}")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "A student's full record: profile, average grade, category levels and enrolments")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<StudentRecordDTO> getRecord(@PathVariable Long number, Authentication authentication) {
    User staff = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.getRecord(number, staff));
  }

  // edit student: answers with the same record as above so the page can just show it

  @PutMapping("{number}")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Edit a student's name, personal email, degree and curricular year")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.DEGREE_NOT_FOUND, ErrorCode.CURRICULAR_YEAR_NOT_IN_DEGREE,
      ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS })
  public ResponseEntity<StudentRecordDTO> editStudent(@PathVariable Long number,
      @Valid @RequestBody EditStudentRequestDTO changes, Authentication authentication) {
    User staff = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.editStudent(number, changes, staff));
  }

  // edit student statute: only staff ever sees the type (GR7)

  @PutMapping("{number}/statute")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Add a special statute or replace the current one, it starts now and ends on endDate")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.STATUTE_END_DATE_IN_PAST })
  public ResponseEntity<StudentRecordDTO> setStatute(@PathVariable Long number,
      @Valid @RequestBody StatuteRequestDTO statute, Authentication authentication) {
    User staff = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.setStatute(number, statute, staff));
  }

  @DeleteMapping("{number}/statute")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "End the student's statute, even an expired one")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.STUDENT_HAS_NO_STATUTE })
  public ResponseEntity<Void> removeStatute(@PathVariable Long number) {
    studentService.removeStatute(number);
    return ResponseEntity.noContent().build();
  }

  // register student

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Register one student, the credentials go to their personal email (emailSent says if it worked)")
  @ErrorCodes({ ErrorCode.DEGREE_NOT_FOUND, ErrorCode.CURRICULAR_YEAR_NOT_IN_DEGREE,
      ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS })
  public ResponseEntity<RegisteredStudentDTO> registerStudent(@Valid @RequestBody RegisterStudentRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(studentService.registerStudent(request));
  }

  @PostMapping("{number}/resend-credentials")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "New one-time password by email, optionally to a corrected personal email")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.CREDENTIALS_ALREADY_USED, ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS,
      ErrorCode.EMAIL_NOT_SENT })
  public ResponseEntity<Void> resendCredentials(@PathVariable Long number,
      @Valid @RequestBody(required = false) ResendCredentialsRequestDTO request) {
    studentService.resendCredentials(number, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("import/template")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Empty students.csv with the columns the import expects")
  public ResponseEntity<String> importTemplate() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csvReader.template());
  }

  // nothing is saved here, it only shows what would happen
  @PostMapping(value = "import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Read a students CSV and check every row, nothing is saved. Row problems come in each row's errors")
  @ErrorCodes({ ErrorCode.CSV_EMPTY, ErrorCode.CSV_MISSING_COLUMNS, ErrorCode.CSV_TOO_MANY_ROWS,
      ErrorCode.CSV_WRONG_ENCODING, ErrorCode.CSV_INVALID, ErrorCode.FILE_TOO_LARGE })
  public ResponseEntity<List<ImportPreviewRowDTO>> previewImport(@RequestPart("file") MultipartFile file)
      throws IOException {
    return ResponseEntity.ok(studentService.previewImport(csvReader.read(file.getBytes())));
  }

  @PostMapping("import")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Create the previewed rows, checked again here. Each row comes back created or rejected")
  @ErrorCodes(ErrorCode.CSV_TOO_MANY_ROWS)
  public ResponseEntity<List<ImportResultRowDTO>> importStudents(@Valid @RequestBody ImportStudentsRequestDTO request) {
    return ResponseEntity.ok(studentService.importStudents(request.rows()));
  }

  // profile

  @GetMapping("me/profile")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Your own profile, private part included")
  public ResponseEntity<StudentProfileDTO> getMyProfile(Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.getProfile(student.getNumber(), student));
  }

  @PutMapping("me/profile")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Edit your banner, photo, about me, skills, interests and languages")
  @ErrorCodes({ ErrorCode.INVALID_BANNER, ErrorCode.INVALID_PROFILE_PHOTO, ErrorCode.AT_LEAST_ONE_LANGUAGE_REQUIRED })
  public ResponseEntity<StudentProfileDTO> updateMyProfile(@Valid @RequestBody UpdateProfileRequestDTO changes,
      Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.updateProfile(student.getNumber(), changes));
  }

  @GetMapping("{number}/profile")
  @Operation(summary = "A student's profile. privateProfile is null unless you're them, staff or one of their teachers")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<StudentProfileDTO> getProfile(@PathVariable Long number, Authentication authentication) {
    User viewer = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.getProfile(number, viewer));
  }

  // private cache: a new photo takes up to 5 minutes to show elsewhere, but lists don't refetch every one
  @GetMapping("{number}/pfp")
  @Operation(summary = "A student's profile photo as an image, for <img src>. NOT_FOUND if they don't have one")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<byte[]> getPfp(@PathVariable Long number) {
    ImageFileDTO pfp = studentService.getPfp(number);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(pfp.contentType()))
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
        .body(pfp.bytes());
  }

  @GetMapping("{number}/ratings")
  @Operation(summary = "The ratings a tutor got, newest first, without who wrote them. Empty if they never tutored")
  public ResponseEntity<List<RatingDTO>> getRatings(@PathVariable Long number) {
    return ResponseEntity.ok(ratingService.listRatings(number));
  }

  // view my groups
  @GetMapping("me/groups")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "The published groups you're in, with each member's name and institutional email. "
      + "Blocked members still show (GR5)")
  public ResponseEntity<List<MyGroupDTO>> getMyGroups(Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(groupService.listMyGroups(student.getNumber()));
  }

  @PostMapping("personality-test")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Send the 20 Mini-IPIP answers (1 to 5), once per academic year. Returns the five profile scores")
  @ErrorCodes(ErrorCode.PERSONALITY_TEST_ALREADY_TAKEN)
  public ResponseEntity<PersonalityProfileDTO> takePersonalityTest(@Valid @RequestBody PersonalityTestRequestDTO test,
      Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.takePersonalityTest(student.getNumber(), test.answers()));
  }

}
