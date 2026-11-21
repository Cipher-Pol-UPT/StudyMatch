package com.studymatch.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.ParticipantDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.dtos.StudyRoomDetailDTO;
import com.studymatch.dtos.UpdateStudyRoomRequestDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Student;
import com.studymatch.models.StudyRoom;
import com.studymatch.models.Subject;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.StudyRoomRepository;
import com.studymatch.utils.MeetLinkGenerator;
import com.studymatch.utils.SearchPatterns;

@Service
public class StudyRoomService {

  private final StudyRoomRepository studyRoomRepository;
  private final TutorRoleService tutorRoleService;
  private final RatingService ratingService;
  private final EnrolmentService enrolmentService;
  private final ApplicationEventPublisher eventPublisher;
  private final MeetLinkGenerator meetLinkGenerator;

  public StudyRoomService(StudyRoomRepository studyRoomRepository, TutorRoleService tutorRoleService,
      RatingService ratingService, EnrolmentService enrolmentService, ApplicationEventPublisher eventPublisher,
      MeetLinkGenerator meetLinkGenerator) {
    this.studyRoomRepository = studyRoomRepository;
    this.tutorRoleService = tutorRoleService;
    this.ratingService = ratingService;
    this.enrolmentService = enrolmentService;
    this.eventPublisher = eventPublisher;
    this.meetLinkGenerator = meetLinkGenerator;
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

    // last, so Google is only asked for a room that will be saved
    if (request.link() == null) {
      room.useGeneratedLink(meetLinkGenerator.createLink());
    }

    return StudyRoomDTO.from(studyRoomRepository.save(room));
  }

  // edit StudyRoom (#51): a tutor changes their room's details, the subject can't change
  @Transactional
  public StudyRoomDTO update(Long tutorNumber, Long roomId, UpdateStudyRoomRequestDTO request) {
    StudyRoom room = getHostedRoom(tutorNumber, roomId);

    // the model checks it's still open and not started, the times and the capacity first,
    // so a past or backwards room is reported before any overlap (the same as create).
    // a null link keeps the current one for now, so the entity never holds a null before the checks pass
    room.update(request.startsAt(), request.endsAt(), request.capacity(), request.description(),
        request.link() != null ? request.link() : room.getLink());

    // the room's own old slot must not count as an overlap, so drop it from the tutor's overlapping rooms
    List<StudyRoom> overlapping = studyRoomRepository.findOverlapping(tutorNumber, request.startsAt(),
        request.endsAt()).stream()
        .filter(other -> !other.getId().equals(roomId))
        .toList();
    if (!overlapping.isEmpty()) {
      StudyRoom conflict = overlapping.get(0);
      throw new StudyMatchException(ErrorCode.STUDYROOM_OVERLAP, Map.of(
          "id", conflict.getId(),
          "subject", conflict.getTutorRole().getSubject().getName(),
          "startsAt", conflict.getStartsAt().toString(),
          "endsAt", conflict.getEndsAt().toString()));
    }

    // last, so Google is only asked for a room that will be saved
    if (request.link() == null) {
      room.useGeneratedLink(meetLinkGenerator.createLink());
    }
    // notifying the participants comes with notifications (#144)
    return StudyRoomDTO.from(studyRoomRepository.save(room));
  }

  // view StudyRooms (#43): the calendar only shows rooms that aren't cancelled and aren't completed,
  // every filter is optional
  @Transactional(readOnly = true)
  public List<StudyRoomDTO> listRooms(Long viewerNumber, Long subjectId, String tutor, String topic, LocalDate date) {
    List<Specification<StudyRoom>> filters = new ArrayList<>();
    filters.add(open());
    if (subjectId != null) {
      filters.add((room, query, where) -> where.equal(room.get("tutorRole").get("subject").get("id"), subjectId));
    }
    if (tutor != null && !tutor.isBlank()) {
      String search = tutor.trim();
      filters.add((room, query, where) -> where.like(
          where.lower(room.get("tutorRole").get("student").get("name")), SearchPatterns.containing(search),
          SearchPatterns.ESCAPE));
    }
    if (topic != null && !topic.isBlank()) {
      String search = topic.trim();
      filters.add((room, query, where) -> where.like(where.lower(room.get("description")),
          SearchPatterns.containing(search), SearchPatterns.ESCAPE));
    }
    if (date != null) {
      LocalDateTime start = date.atStartOfDay();
      filters.add((room, query, where) -> where.and(
          where.greaterThanOrEqualTo(room.get("startsAt"), start),
          where.lessThan(room.get("startsAt"), start.plusDays(1))));
    }
    return studyRoomRepository.findAll(Specification.allOf(filters), Sort.by("startsAt")).stream()
        .map(room -> StudyRoomDTO.from(room, canSeeLink(room, viewerNumber)))
        .toList();
  }

  @Transactional(readOnly = true)
  public StudyRoomDetailDTO getRoom(Long roomId, Long viewerNumber) {
    StudyRoom room = studyRoomRepository.findById(roomId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    TutorRole tutorRole = room.getTutorRole();
    boolean hosted = tutorRole.getStudent().getNumber().equals(viewerNumber);
    boolean joined = room.getParticipations().stream()
        .anyMatch(participation -> participation.getStudent().getNumber().equals(viewerNumber));
    // the same checks the Join use case (#45) enforces, so the option is only shown when it can work
    boolean canJoin = !hosted && !joined && room.getStatus() == StudyRoomStatus.SCHEDULED
        && LocalDateTime.now().isBefore(room.getStartsAt()) && isEligible(viewerNumber, tutorRole.getSubject());
    return StudyRoomDetailDTO.from(room, ratingService.getAverageStars(tutorRole),
        ratingService.countRatings(tutorRole), hosted, joined, canJoin);
  }

  // join StudyRoom (#45): eligible when enrolled in the subject's current edition and hasn't passed it
  private boolean isEligible(Long studentNumber, Subject subject) {
    return enrolmentService.isEnrolledInCurrentEdition(studentNumber, subject)
        && !enrolmentService.hasPassed(studentNumber, subject);
  }

  // the link is private: only the tutor and the students who joined see it
  private boolean canSeeLink(StudyRoom room, Long viewerNumber) {
    return room.getTutorRole().getStudent().getNumber().equals(viewerNumber)
        || room.getParticipations().stream()
            .anyMatch(participation -> participation.getStudent().getNumber().equals(viewerNumber));
  }

  // stored SCHEDULED and still inside the 48h confirmation window, so getStatus() isn't COMPLETED
  private static Specification<StudyRoom> open() {
    return (room, query, where) -> where.and(
        where.equal(room.get("status"), StudyRoomStatus.SCHEDULED),
        where.greaterThanOrEqualTo(room.get("endsAt"),
            LocalDateTime.now().minusHours(StudyRoom.CONFIRMATION_WINDOW_HOURS)));
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

  @Transactional
  public StudyRoomDTO join(Student student, Long roomId) {
    // locked so two students joining the last place at the same time don't both get in
    StudyRoom room = studyRoomRepository.findLockedById(roomId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    Long studentNumber = student.getNumber();

    // eligibility (#45): enrolled in the subject's current edition and hasn't passed it
    if (!isEligible(studentNumber, room.getTutorRole().getSubject())) {
      throw new StudyMatchException(ErrorCode.NOT_ELIGIBLE_FOR_STUDYROOM);
    }

    // a student can't be in two rooms at the same time, whether they joined one or host it
    List<StudyRoom> overlapping = new ArrayList<>();
    overlapping.addAll(studyRoomRepository.findJoinedOverlapping(studentNumber, roomId, room.getStartsAt(),
        room.getEndsAt()));
    overlapping.addAll(studyRoomRepository.findOverlapping(studentNumber, room.getStartsAt(), room.getEndsAt()));
    if (!overlapping.isEmpty()) {
      StudyRoom conflict = overlapping.get(0);
      throw new StudyMatchException(ErrorCode.JOINED_STUDYROOM_OVERLAP, Map.of(
          "id", conflict.getId(),
          "subject", conflict.getTutorRole().getSubject().getName(),
          "startsAt", conflict.getStartsAt().toString(),
          "endsAt", conflict.getEndsAt().toString()));
    }

    // the model checks it's open, not started, not the tutor, not already joined, and not full
    room.join(student);
    // notifying the tutor comes with notifications (#144)
    return StudyRoomDTO.from(studyRoomRepository.save(room));
  }

  @Transactional
  public StudyRoomDTO leave(Student student, Long roomId) {
    StudyRoom room = studyRoomRepository.findById(roomId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    // the model checks it's still open, not started, and the student is a participant
    room.leave(student);
    // notifying the tutor comes with notifications (#144)
    return StudyRoomDTO.from(studyRoomRepository.save(room));
  }

  // cancel StudyRoom (#53): a tutor cancels their room before it starts, with an optional reason
  @Transactional
  public StudyRoomDTO cancel(Long tutorNumber, Long roomId, String reason) {
    StudyRoom room = getHostedRoom(tutorNumber, roomId);
    // the model checks it's still open and not started
    room.cancel(reason);
    // notifying the participants, with the reason if given, comes with notifications (#144)
    return StudyRoomDTO.from(studyRoomRepository.save(room));
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
