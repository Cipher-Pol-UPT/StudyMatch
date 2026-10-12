package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.TutorRoleService;

@RestController
@RequestMapping("/tutors/")
public class TutorRoleController {

  private final TutorRoleService tutorRoleService;

  public TutorRoleController(TutorRoleService tutorRoleService) {
    this.tutorRoleService = tutorRoleService;
  }

}
