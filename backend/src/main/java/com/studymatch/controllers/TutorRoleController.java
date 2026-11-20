package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.TutorEligibleSubjectDTO;
import com.studymatch.dtos.TutorSubjectDTO;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.services.TutorApplicationService;
import com.studymatch.services.TutorRoleService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/tutors/")
@Tag(name = "Tutors", description = "Students who tutor a subject")
public class TutorRoleController {

  private final TutorRoleService tutorRoleService;
  private final TutorApplicationService tutorApplicationService;
  private final UserService userService;

  public TutorRoleController(TutorRoleService tutorRoleService, TutorApplicationService tutorApplicationService,
      UserService userService) {
    this.tutorRoleService = tutorRoleService;
    this.tutorApplicationService = tutorApplicationService;
    this.userService = userService;
  }

  // create StudyRoom: the subjects the logged-in student tutors, sorted by name
  @GetMapping("me/subjects")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "The subjects you tutor, by name. Empty if you don't tutor any")
  public ResponseEntity<List<TutorSubjectDTO>> getMySubjects(Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(tutorRoleService.getTutoredSubjects(student.getNumber()));
  }

  // apply for tutoring (#41): the subjects the student passed, with their rank and eligibility
  @GetMapping("me/applicable-subjects")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "The subjects you passed, with your rank and whether you can apply to tutor them")
  public ResponseEntity<List<TutorEligibleSubjectDTO>> getApplicableSubjects(Authentication authentication) {
    // hasRole('STUDENT') guarantees this is a Student
    Student student = (Student) userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(tutorApplicationService.listApplicableSubjects(student));
  }

}
