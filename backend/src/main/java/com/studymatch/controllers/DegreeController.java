package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.DegreeService;

@RestController
@RequestMapping("/degrees/")
public class DegreeController {

  private final DegreeService degreeService;

  public DegreeController(DegreeService degreeService) {
    this.degreeService = degreeService;
  }

}
