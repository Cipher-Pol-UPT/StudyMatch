package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.TutorSubjectDTO;
import com.studymatch.models.User;
import com.studymatch.services.TutorRoleService;
import com.studymatch.services.UserService;

@RestController
@RequestMapping("/tutors/")
public class TutorRoleController {

  private final TutorRoleService tutorRoleService;
  private final UserService userService;

  public TutorRoleController(TutorRoleService tutorRoleService, UserService userService) {
    this.tutorRoleService = tutorRoleService;
    this.userService = userService;
  }

  // create StudyRoom: the subjects the logged-in student tutors, sorted by name
  @GetMapping("me/subjects")
  @PreAuthorize("hasRole('STUDENT')")
  public ResponseEntity<List<TutorSubjectDTO>> getMySubjects(Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(tutorRoleService.getTutoredSubjects(student.getNumber()));
  }

}
