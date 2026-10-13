package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.AbsenceService;

@RestController
@RequestMapping("/absences/")
public class AbsenceController {

  private final AbsenceService absenceService;

  public AbsenceController(AbsenceService absenceService) {
    this.absenceService = absenceService;
  }

}
