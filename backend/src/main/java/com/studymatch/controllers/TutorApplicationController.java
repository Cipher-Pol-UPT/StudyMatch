package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ApplyTutorRequestDTO;
import com.studymatch.dtos.TutorApplicationDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Student;
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

}
