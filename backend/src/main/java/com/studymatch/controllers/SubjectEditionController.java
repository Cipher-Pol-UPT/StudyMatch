package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.SubjectEditionService;

@RestController
@RequestMapping("/subject-editions/")
public class SubjectEditionController {

  private final SubjectEditionService subjectEditionService;

  public SubjectEditionController(SubjectEditionService subjectEditionService) {
    this.subjectEditionService = subjectEditionService;
  }

}
