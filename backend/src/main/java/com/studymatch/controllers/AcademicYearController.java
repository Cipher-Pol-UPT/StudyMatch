package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.AcademicYearResponseDTO;
import com.studymatch.services.AcademicYearService;

@RestController
@RequestMapping("/academic-years/")
public class AcademicYearController {

  private final AcademicYearService academicYearService;

  public AcademicYearController(AcademicYearService academicYearService) {
    this.academicYearService = academicYearService;
  }

  // any logged-in user: Staff picks one for a subject edition, everyone else sees them on editions
  @GetMapping
  public ResponseEntity<List<AcademicYearResponseDTO>> listAcademicYears() {
    return ResponseEntity.ok(academicYearService.listAcademicYears().stream().map(AcademicYearResponseDTO::from).toList());
  }

}
