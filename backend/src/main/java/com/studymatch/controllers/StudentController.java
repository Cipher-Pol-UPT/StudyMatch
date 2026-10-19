package com.studymatch.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.studymatch.dtos.ImportPreviewRowDTO;
import com.studymatch.dtos.ImportResultRowDTO;
import com.studymatch.dtos.ImportStudentsRequestDTO;
import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.dtos.PersonalityTestRequestDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;
import com.studymatch.dtos.RegisteredStudentDTO;
import com.studymatch.dtos.ResendCredentialsRequestDTO;
import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.models.User;
import com.studymatch.services.StudentService;
import com.studymatch.services.UserService;
import com.studymatch.utils.StudentCsvReader;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/students/")
public class StudentController {

  private final StudentService studentService;
  private final UserService userService;
  private final StudentCsvReader csvReader;

  public StudentController(StudentService studentService, UserService userService, StudentCsvReader csvReader) {
    this.studentService = studentService;
    this.userService = userService;
    this.csvReader = csvReader;
  }

  // register student

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<RegisteredStudentDTO> registerStudent(@Valid @RequestBody RegisterStudentRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(studentService.registerStudent(request));
  }

  @PostMapping("{number}/resend-credentials")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<Void> resendCredentials(@PathVariable Long number,
      @Valid @RequestBody(required = false) ResendCredentialsRequestDTO request) {
    studentService.resendCredentials(number, request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("import/template")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<String> importTemplate() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students.csv\"")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .body(csvReader.template());
  }

  // nothing is saved here, it only shows what would happen
  @PostMapping(value = "import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<List<ImportPreviewRowDTO>> previewImport(@RequestPart("file") MultipartFile file)
      throws IOException {
    return ResponseEntity.ok(studentService.previewImport(csvReader.read(file.getBytes())));
  }

  @PostMapping("import")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<List<ImportResultRowDTO>> importStudents(@Valid @RequestBody ImportStudentsRequestDTO request) {
    return ResponseEntity.ok(studentService.importStudents(request.rows()));
  }

  // profile

  @GetMapping("me/profile")
  @PreAuthorize("hasRole('STUDENT')")
  public ResponseEntity<StudentProfileDTO> getMyProfile(Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.getProfile(student.getNumber(), student));
  }

  @PutMapping("me/profile")
  @PreAuthorize("hasRole('STUDENT')")
  public ResponseEntity<StudentProfileDTO> updateMyProfile(@Valid @RequestBody UpdateProfileRequestDTO changes,
      Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.updateProfile(student.getNumber(), changes));
  }

  @GetMapping("{number}/profile")
  public ResponseEntity<StudentProfileDTO> getProfile(@PathVariable Long number, Authentication authentication) {
    User viewer = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.getProfile(number, viewer));
  }

  @PostMapping("personality-test")
  @PreAuthorize("hasRole('STUDENT')")
  public ResponseEntity<PersonalityProfileDTO> takePersonalityTest(@Valid @RequestBody PersonalityTestRequestDTO test,
      Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studentService.takePersonalityTest(student.getNumber(), test.answers()));
  }

}
