package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.models.User;
import com.studymatch.services.EnrolmentService;
import com.studymatch.services.SubjectEditionService;
import com.studymatch.services.UserService;

@RestController
@RequestMapping("/subject-editions/")
public class SubjectEditionController {

  private final SubjectEditionService subjectEditionService;
  private final EnrolmentService enrolmentService;
  private final UserService userService;

  public SubjectEditionController(SubjectEditionService subjectEditionService, EnrolmentService enrolmentService,
      UserService userService) {
    this.subjectEditionService = subjectEditionService;
    this.enrolmentService = enrolmentService;
    this.userService = userService;
  }

  @GetMapping
  @PreAuthorize("hasRole('TEACHER')")
  public ResponseEntity<List<SubjectEditionResponseDTO>> listMyEditions(Authentication authentication) {
    User teacher = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(subjectEditionService.listTaughtBy(teacher.getNumber()));
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

}
