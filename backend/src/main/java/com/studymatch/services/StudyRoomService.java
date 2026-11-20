package com.studymatch.services;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.ParticipantDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.StudyRoomRepository;

@Service
public class StudyRoomService {

  private final StudyRoomRepository studyRoomRepository;
  private final TutorRoleService tutorRoleService;
  private final ApplicationEventPublisher eventPublisher;

  public StudyRoomService(StudyRoomRepository studyRoomRepository, TutorRoleService tutorRoleService,
      ApplicationEventPublisher eventPublisher) {
    this.studyRoomRepository = studyRoomRepository;
    this.tutorRoleService = tutorRoleService;
    this.eventPublisher = eventPublisher;
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

  public int countUpcoming(Long tutorNumber) {
    return studyRoomRepository.findUpcoming(tutorNumber, LocalDateTime.now()).size();
  }

  // achievements: the tutor's rooms with enough confirmed attendees to count
  public long countCounted(Long tutorNumber) {
    return studyRoomRepository.countWithAttendees(tutorNumber, Achievement.MIN_ATTENDEES);
  }

  // block account: the tutor's rooms that haven't started are cancelled, the block reason stays private
  @Transactional
  public int cancelUpcoming(Long tutorNumber) {
    List<StudyRoom> rooms = studyRoomRepository.findUpcoming(tutorNumber, LocalDateTime.now());
    rooms.forEach(room -> room.cancel(null));
    studyRoomRepository.saveAll(rooms);
    return rooms.size();
  }

  @Transactional(readOnly = true)
  public List<ParticipantDTO> listParticipants(Long tutorNumber, Long roomId) {
    return getHostedRoom(tutorNumber, roomId).getParticipations().stream()
        .map(ParticipantDTO::from)
        .sorted(Comparator.comparing(ParticipantDTO::name))
        .toList();
  }

  @Transactional
  public StudyRoomDTO confirmAttendance(Long tutorNumber, Long roomId, Set<Long> attendeeNumbers) {
    StudyRoom room = getHostedRoom(tutorNumber, roomId);
    room.confirmAttendance(attendeeNumbers);
    // telling the attendees they can rate comes with notifications (#144)
    StudyRoom saved = studyRoomRepository.save(room);
    eventPublisher.publishEvent(new TutorActivityEvent(saved.getTutorRole().getStudent()));
    return StudyRoomDTO.from(saved);
  }

  // only the tutor who hosts the room can see who joined and confirm it
  private StudyRoom getHostedRoom(Long tutorNumber, Long roomId) {
    StudyRoom room = studyRoomRepository.findById(roomId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    if (!room.getTutorRole().getStudent().getNumber().equals(tutorNumber)) {
      throw new StudyMatchException(ErrorCode.ACCESS_DENIED);
    }
    return room;
  }

}
