package com.studymatch.controllers;

import java.util.List;

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

import com.studymatch.dtos.ApplyTutorRequestDTO;
import com.studymatch.dtos.PendingTutorApplicationDTO;
import com.studymatch.dtos.RejectTutorRequestDTO;
import com.studymatch.dtos.TutorApplicationDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.services.TutorApplicationService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/tutor-applications/")
@Tag(name = "TutorApplications", description = "Students who asked to tutor a subject")
public class TutorApplicationController {

  private final TutorApplicationService tutorApplicationService;
  private final UserService userService;

  public TutorApplicationController(TutorApplicationService tutorApplicationService, UserService userService) {
    this.tutorApplicationService = tutorApplicationService;
    this.userService = userService;
  }

  @PostMapping
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Apply to tutor a subject you passed, if your grade is in the top 5% of your edition")
  @ErrorCodes({ ErrorCode.NOT_ELIGIBLE_FOR_TUTORING, ErrorCode.TUTOR_APPLICATION_ALREADY_PENDING,
      ErrorCode.TUTOR_ROLE_ALREADY_EXISTS, ErrorCode.SUBJECT_NOT_TAUGHT_THIS_YEAR })
  public ResponseEntity<TutorApplicationDTO> apply(@Valid @RequestBody ApplyTutorRequestDTO request,
      Authentication authentication) {
    // hasRole('STUDENT') guarantees this is a Student
    Student student = (Student) userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(tutorApplicationService.apply(student, request));
  }

  @GetMapping
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "The pending applications for a subject you teach, with the applicant's grade, rank and ratings")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACCESS_DENIED })
  public ResponseEntity<List<PendingTutorApplicationDTO>> listPending(@RequestParam Long subjectId,
      Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(tutorApplicationService.listPendingApplications(teacher.getNumber(), subjectId));
  }

  @PostMapping("{id}/approve")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Approve a tutor application for a subject you teach, making the student its tutor")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACCESS_DENIED, ErrorCode.APPLICATION_ALREADY_DECIDED })
  public ResponseEntity<TutorApplicationDTO> approve(@PathVariable Long id, Authentication authentication) {
    // hasRole('TEACHER') guarantees this is a Teacher
    Teacher teacher = (Teacher) userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(tutorApplicationService.approve(teacher, id));
  }

  @PostMapping("{id}/reject")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Reject a tutor application for a subject you teach, with an optional reason")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACCESS_DENIED, ErrorCode.APPLICATION_ALREADY_DECIDED })
  public ResponseEntity<TutorApplicationDTO> reject(@PathVariable Long id,
      @Valid @RequestBody(required = false) RejectTutorRequestDTO request, Authentication authentication) {
    // hasRole('TEACHER') guarantees this is a Teacher
    Teacher teacher = (Teacher) userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(tutorApplicationService.reject(teacher, id, request == null ? null : request.reason()));
  }

}
