package com.studymatch.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.StudyRoomRepository;

@Service
public class StudyRoomService {

  private final StudyRoomRepository studyRoomRepository;
  private final TutorRoleService tutorRoleService;

  public StudyRoomService(StudyRoomRepository studyRoomRepository, TutorRoleService tutorRoleService) {
    this.studyRoomRepository = studyRoomRepository;
    this.tutorRoleService = tutorRoleService;
  }

  @Transactional
  public StudyRoomDTO create(Long tutorNumber, CreateStudyRoomRequestDTO request) {
    TutorRole tutorRole = tutorRoleService.getByStudentAndSubject(tutorNumber, request.subjectId());

    // the constructor checks the times first, so a past or backwards room is reported before any overlap
    StudyRoom room = new StudyRoom(tutorRole, request.startsAt(), request.endsAt(), request.capacity(),
        request.description(), request.link());

    List<StudyRoom> overlapping = studyRoomRepository.findOverlapping(tutorNumber, request.startsAt(),
        request.endsAt());
    if (!overlapping.isEmpty()) {
      StudyRoom conflict = overlapping.get(0);
      throw new StudyMatchException(ErrorCode.STUDYROOM_OVERLAP, Map.of(
          "id", conflict.getId(),
          "subject", conflict.getTutorRole().getSubject().getName(),
          "startsAt", conflict.getStartsAt().toString(),
          "endsAt", conflict.getEndsAt().toString()));
    }

    return StudyRoomDTO.from(studyRoomRepository.save(room));
  }

}
