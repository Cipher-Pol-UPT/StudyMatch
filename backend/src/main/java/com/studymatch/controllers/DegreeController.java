package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.DegreeDTO;
import com.studymatch.services.DegreeService;

@RestController
@RequestMapping("/degrees/")
public class DegreeController {

  private final DegreeService degreeService;

  public DegreeController(DegreeService degreeService) {
    this.degreeService = degreeService;
  }

  // for the degree pickers (register student, later subjects too)
  @GetMapping
  public ResponseEntity<List<DegreeDTO>> listDegrees() {
    return ResponseEntity.ok(degreeService.findAll().stream().map(DegreeDTO::from).toList());
  }

}
