package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Participation;
import com.studymatch.repositories.ParticipationRepository;

@Service
public class ParticipationService {

  private final ParticipationRepository participationRepository;

  public ParticipationService(ParticipationRepository participationRepository) {
    this.participationRepository = participationRepository;
  }

  // a room that doesn't exist gives the same answer, the student wasn't in it either way
  public Participation getParticipation(Long studyRoomId, Long studentNumber) {
    return participationRepository.findByStudyRoom_IdAndStudent_Number(studyRoomId, studentNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_A_PARTICIPANT));
  }

}
