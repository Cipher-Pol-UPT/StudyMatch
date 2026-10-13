package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.ClassSectionService;

@RestController
@RequestMapping("/classes/")
public class ClassSectionController {

  private final ClassSectionService classSectionService;

  public ClassSectionController(ClassSectionService classSectionService) {
    this.classSectionService = classSectionService;
  }

}
