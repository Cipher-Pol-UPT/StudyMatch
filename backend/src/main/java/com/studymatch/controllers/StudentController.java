package com.studymatch.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.models.User;
import com.studymatch.services.StudentService;
import com.studymatch.services.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/students/")
public class StudentController {

  private final StudentService studentService;
  private final UserService userService;

  public StudentController(StudentService studentService, UserService userService) {
    this.studentService = studentService;
    this.userService = userService;
  }

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

}
