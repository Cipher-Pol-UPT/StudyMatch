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

import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.dtos.SubjectResponseDTO;
import com.studymatch.models.SubjectEdition;
import com.studymatch.services.SubjectEditionService;
import com.studymatch.services.SubjectService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/subjects/")
public class SubjectController {

  private final SubjectService subjectService;
  private final SubjectEditionService subjectEditionService;

  public SubjectController(SubjectService subjectService, SubjectEditionService subjectEditionService) {
    this.subjectService = subjectService;
    this.subjectEditionService = subjectEditionService;
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

  // registers the subject and its first edition, the answer is the edition with the subject inside
  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<SubjectEditionResponseDTO> registerSubject(@Valid @RequestBody SubjectRequestDTO registerRequest) {
    SubjectEdition edition = subjectService.registerSubject(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(SubjectEditionResponseDTO.from(edition));
  }

  // when the code is already taken, Staff can still add an edition of that subject for another academic year
  @PostMapping("{id}/editions")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<SubjectEditionResponseDTO> registerEdition(@PathVariable Long id,
      @Valid @RequestBody SubjectEditionRequestDTO editionRequest) {
    SubjectEdition edition = subjectEditionService.registerEdition(id, editionRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(SubjectEditionResponseDTO.from(edition));
  }

}
