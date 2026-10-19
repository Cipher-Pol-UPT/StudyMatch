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

import com.studymatch.dtos.DegreeRequestDTO;
import com.studymatch.dtos.DegreeResponseDTO;
import com.studymatch.models.Degree;
import com.studymatch.services.DegreeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/degrees/")
public class DegreeController {

  private final DegreeService degreeService;

  public DegreeController(DegreeService degreeService) {
    this.degreeService = degreeService;
  }

  // any logged-in user: Staff pick them for students and subjects, everyone else sees them on profiles
  @GetMapping
  public ResponseEntity<List<DegreeResponseDTO>> listDegrees() {
    return ResponseEntity.ok(degreeService.listDegrees().stream().map(DegreeResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  public ResponseEntity<DegreeResponseDTO> getDegree(@PathVariable Long id) {
    return ResponseEntity.ok(DegreeResponseDTO.from(degreeService.getDegree(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<DegreeResponseDTO> registerDegree(@Valid @RequestBody DegreeRequestDTO registerRequest) {
    Degree degree = degreeService.registerDegree(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(DegreeResponseDTO.from(degree));
  }

}
