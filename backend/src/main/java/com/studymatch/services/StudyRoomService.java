package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.StudyRoomRepository;

@Service
public class StudyRoomService {

  private final StudyRoomRepository studyRoomRepository;

  public StudyRoomService(StudyRoomRepository studyRoomRepository) {
    this.studyRoomRepository = studyRoomRepository;
  }

}
