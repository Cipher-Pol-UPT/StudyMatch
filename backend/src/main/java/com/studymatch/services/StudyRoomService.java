package com.studymatch.services;

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
import com.studymatch.repositories.TutorRoleRepository;

@Service
public class StudyRoomService {

  private final StudyRoomRepository studyRoomRepository;
  private final TutorRoleRepository tutorRoleRepository;

  public StudyRoomService(StudyRoomRepository studyRoomRepository, TutorRoleRepository tutorRoleRepository) {
    this.studyRoomRepository = studyRoomRepository;
    this.tutorRoleRepository = tutorRoleRepository;
  }

  @Transactional
  public StudyRoomDTO create(Long tutorNumber, CreateStudyRoomRequestDTO request) {
    TutorRole tutorRole = tutorRoleRepository.findByStudentNumberAndSubjectId(tutorNumber, request.subjectId())
        .orElseThrow(() -> new StudyMatchException(ErrorCode.ACCESS_DENIED));

    // the constructor checks the times first, so a past or backwards room is reported before any overlap
    StudyRoom room = new StudyRoom(tutorRole, request.startsAt(), request.endsAt(), request.capacity(),
        request.description(), request.link());

    studyRoomRepository.findOverlapping(tutorNumber, request.startsAt(), request.endsAt()).stream()
        .findFirst()
        .ifPresent(conflict -> {
          throw new StudyMatchException(ErrorCode.STUDYROOM_OVERLAP, Map.of(
              "id", conflict.getId(),
              "subject", conflict.getTutorRole().getSubject().getName(),
              "startsAt", conflict.getStartsAt().toString(),
              "endsAt", conflict.getEndsAt().toString()));
        });

    return StudyRoomDTO.from(studyRoomRepository.save(room));
  }

}
