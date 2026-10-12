package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.AcademicYearService;

@RestController
@RequestMapping("/academic-years/")
public class AcademicYearController {

  private final AcademicYearService academicYearService;

  public AcademicYearController(AcademicYearService academicYearService) {
    this.academicYearService = academicYearService;
  }

}
