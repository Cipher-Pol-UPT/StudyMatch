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
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.SubjectEdition;
import com.studymatch.services.SubjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/subjects/")
@Tag(name = "Subjects", description = "Subjects and their editions per academic year")
public class SubjectController {

  private final SubjectService subjectService;

  public SubjectController(SubjectService subjectService) {
    this.subjectService = subjectService;
  }

  // any logged-in user: students, teachers and Staff all browse the subjects of the degrees
  @GetMapping
  @Operation(summary = "Every subject, with its category and the degrees it belongs to")
  public ResponseEntity<List<SubjectResponseDTO>> listSubjects() {
    return ResponseEntity.ok(subjectService.listSubjects().stream().map(SubjectResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  @Operation(summary = "One subject")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<SubjectResponseDTO> getSubject(@PathVariable Long id) {
    return ResponseEntity.ok(SubjectResponseDTO.from(subjectService.getSubject(id)));
  }

  // registers the subject and its first edition, the answer is the edition with the subject inside
  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Register a subject and its first edition. A taken code answers with the existing subject's id")
  @ErrorCodes({ ErrorCode.SUBJECT_ALREADY_EXISTS, ErrorCode.NOT_FOUND, ErrorCode.CURRICULAR_YEAR_OUT_OF_RANGE,
      ErrorCode.SUBJECT_ALREADY_IN_DEGREE, ErrorCode.AT_LEAST_ONE_TEACHER_REQUIRED,
      ErrorCode.CLASS_SECTION_ALREADY_EXISTS })
  public ResponseEntity<SubjectEditionResponseDTO> registerSubject(@Valid @RequestBody SubjectRequestDTO registerRequest) {
    SubjectEdition edition = subjectService.registerSubject(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(SubjectEditionResponseDTO.from(edition));
  }

  // when the code is already taken, Staff can still add an edition of that subject for another academic year
  @PostMapping("{id}/editions")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Add an edition of an existing subject for another academic year, with its teachers and classes")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS, ErrorCode.AT_LEAST_ONE_TEACHER_REQUIRED,
      ErrorCode.CLASS_SECTION_ALREADY_EXISTS })
  public ResponseEntity<SubjectEditionResponseDTO> registerEdition(@PathVariable Long id,
      @Valid @RequestBody SubjectEditionRequestDTO editionRequest) {
    SubjectEdition edition = subjectService.registerEdition(id, editionRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(SubjectEditionResponseDTO.from(edition));
  }

}
