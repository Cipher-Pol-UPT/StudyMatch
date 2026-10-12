package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.TutorApplicationService;

@RestController
@RequestMapping("/tutor-applications/")
public class TutorApplicationController {

  private final TutorApplicationService tutorApplicationService;

  public TutorApplicationController(TutorApplicationService tutorApplicationService) {
    this.tutorApplicationService = tutorApplicationService;
  }

}
