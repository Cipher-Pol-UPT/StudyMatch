package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.EnrolmentService;

@RestController
@RequestMapping("/enrolments/")
public class EnrolmentController {

  private final EnrolmentService enrolmentService;

  public EnrolmentController(EnrolmentService enrolmentService) {
    this.enrolmentService = enrolmentService;
  }

}
