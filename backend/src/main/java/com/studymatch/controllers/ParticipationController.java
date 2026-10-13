package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.ParticipationService;

@RestController
@RequestMapping("/participations/")
public class ParticipationController {

  private final ParticipationService participationService;

  public ParticipationController(ParticipationService participationService) {
    this.participationService = participationService;
  }

}
