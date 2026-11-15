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
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Degree;
import com.studymatch.services.DegreeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/degrees/")
@Tag(name = "Degrees", description = "Degree programmes and how many curricular years they have")
public class DegreeController {

  private final DegreeService degreeService;

  public DegreeController(DegreeService degreeService) {
    this.degreeService = degreeService;
  }

  // any logged-in user: Staff pick them for students and subjects, everyone else sees them on profiles
  @GetMapping
  @Operation(summary = "Every degree")
  public ResponseEntity<List<DegreeResponseDTO>> listDegrees() {
    return ResponseEntity.ok(degreeService.listDegrees().stream().map(DegreeResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  @Operation(summary = "One degree")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<DegreeResponseDTO> getDegree(@PathVariable Long id) {
    return ResponseEntity.ok(DegreeResponseDTO.from(degreeService.getDegree(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Register a degree")
  @ErrorCodes(ErrorCode.DEGREE_ALREADY_EXISTS)
  public ResponseEntity<DegreeResponseDTO> registerDegree(@Valid @RequestBody DegreeRequestDTO registerRequest) {
    Degree degree = degreeService.registerDegree(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(DegreeResponseDTO.from(degree));
  }

}
