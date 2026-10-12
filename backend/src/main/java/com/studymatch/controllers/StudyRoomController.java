package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.StudyRoomService;

@RestController
@RequestMapping("/study-rooms/")
public class StudyRoomController {

  private final StudyRoomService studyRoomService;

  public StudyRoomController(StudyRoomService studyRoomService) {
    this.studyRoomService = studyRoomService;
  }

}
