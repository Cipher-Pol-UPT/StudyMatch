package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.dtos.SubjectResponseDTO;
import com.studymatch.models.Subject;
import com.studymatch.services.SubjectService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subjects/")
public class SubjectController {

  private final SubjectService subjectService;

  public SubjectController(SubjectService subjectService) {
    this.subjectService = subjectService;
  }

  // any logged-in user: students, teachers and Staff all browse the subjects of the degrees
  @GetMapping
  public ResponseEntity<List<SubjectResponseDTO>> listSubjects() {
    return ResponseEntity.ok(subjectService.listSubjects().stream().map(SubjectResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  public ResponseEntity<SubjectResponseDTO> getSubject(@PathVariable Long id) {
    return ResponseEntity.ok(SubjectResponseDTO.from(subjectService.getSubject(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<SubjectResponseDTO> registerSubject(@Valid @RequestBody SubjectRequestDTO registerRequest) {
    Subject subject = subjectService.registerSubject(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(SubjectResponseDTO.from(subject));
  }

}
