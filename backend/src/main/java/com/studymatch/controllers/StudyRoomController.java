package com.studymatch.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.CancelStudyRoomRequestDTO;
import com.studymatch.dtos.ConfirmAttendanceRequestDTO;
import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.ParticipantDTO;
import com.studymatch.dtos.RateTutorRequestDTO;
import com.studymatch.dtos.RatingDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.dtos.StudyRoomDetailDTO;
import com.studymatch.dtos.UpdateStudyRoomRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.services.RatingService;
import com.studymatch.services.StudyRoomService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/study-rooms/")
@Tag(name = "StudyRooms", description = "Online study sessions hosted by tutors")
public class StudyRoomController {

  private final StudyRoomService studyRoomService;
  private final RatingService ratingService;
  private final UserService userService;

  public StudyRoomController(StudyRoomService studyRoomService, RatingService ratingService,
      UserService userService) {
    this.studyRoomService = studyRoomService;
    this.ratingService = ratingService;
    this.userService = userService;
  }

  @PostMapping
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Create a StudyRoom for a subject you tutor. ACCESS_DENIED if you don't tutor it. "
      + "Without a link the server creates the Google Meet link, MEET_LINK_UNAVAILABLE if Google can't")
  @ErrorCodes({ ErrorCode.STUDYROOM_IN_PAST, ErrorCode.STUDYROOM_ENDS_BEFORE_START, ErrorCode.STUDYROOM_OVERLAP,
      ErrorCode.MEET_LINK_UNAVAILABLE })
  public ResponseEntity<StudyRoomDTO> create(@Valid @RequestBody CreateStudyRoomRequestDTO request,
      Authentication authentication) {
    User tutor = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(studyRoomService.create(tutor.getNumber(), request));
  }

  @PutMapping("{id}")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Edit a StudyRoom you host before it starts. The subject can't change, "
      + "cancel and create a new room instead")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACCESS_DENIED, ErrorCode.STUDYROOM_CLOSED,
      ErrorCode.STUDYROOM_ALREADY_STARTED, ErrorCode.STUDYROOM_IN_PAST, ErrorCode.STUDYROOM_ENDS_BEFORE_START,
      ErrorCode.STUDYROOM_OVERLAP, ErrorCode.CAPACITY_BELOW_PARTICIPANTS, ErrorCode.MEET_LINK_UNAVAILABLE })
  public ResponseEntity<StudyRoomDTO> update(@PathVariable Long id,
      @Valid @RequestBody UpdateStudyRoomRequestDTO request, Authentication authentication) {
    User tutor = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.update(tutor.getNumber(), id, request));
  }

  @GetMapping
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "The StudyRooms still open, sorted by start, filtered by subject, tutor, topic or date. "
      + "The link is null unless you're the tutor or already joined")
  public ResponseEntity<List<StudyRoomDTO>> list(@RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) String tutor, @RequestParam(required = false) String topic,
      @RequestParam(required = false) LocalDate date, Authentication authentication) {
    User viewer = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.listRooms(viewer.getNumber(), subjectId, tutor, topic, date));
  }

  @GetMapping("{id}")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "One StudyRoom's full description, the tutor's rating in that subject, and whether you can join. "
      + "The link is null unless you're the tutor or already joined")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<StudyRoomDetailDTO> get(@PathVariable Long id, Authentication authentication) {
    User viewer = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.getRoom(id, viewer.getNumber()));
  }

  @GetMapping("{id}/participants")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Who joined a StudyRoom you host, by name, and whether they attended")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<List<ParticipantDTO>> listParticipants(@PathVariable Long id, Authentication authentication) {
    User tutor = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.listParticipants(tutor.getNumber(), id));
  }

  @PostMapping("{id}/attendance")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Confirm who attended a StudyRoom you host once it ended, the rest count as absent. "
      + "The room becomes COMPLETED. After 48 hours without this it's completed with nobody attended")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.STUDYROOM_NOT_ENDED, ErrorCode.STUDYROOM_CLOSED,
      ErrorCode.ATTENDEE_NOT_A_PARTICIPANT })
  public ResponseEntity<StudyRoomDTO> confirmAttendance(@PathVariable Long id,
      @Valid @RequestBody ConfirmAttendanceRequestDTO request, Authentication authentication) {
    User tutor = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.confirmAttendance(tutor.getNumber(), id, request.attendeeNumbers()));
  }

  @PostMapping("{id}/rating")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Rate the tutor of a StudyRoom you attended, 1 to 5 stars and an optional public comment. "
      + "Once per room, up to 7 days after it ended")
  @ErrorCodes({ ErrorCode.NOT_A_PARTICIPANT, ErrorCode.ATTENDANCE_NOT_CONFIRMED, ErrorCode.RATING_WINDOW_CLOSED,
      ErrorCode.ROOM_ALREADY_RATED })
  public ResponseEntity<RatingDTO> rate(@PathVariable Long id, @Valid @RequestBody RateTutorRequestDTO request,
      Authentication authentication) {
    User student = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(id, student.getNumber(), request));
  }

  @PostMapping("{id}/join")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Join an open StudyRoom, if you're enrolled in the subject and haven't passed it")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NOT_ELIGIBLE_FOR_STUDYROOM, ErrorCode.JOINED_STUDYROOM_OVERLAP,
      ErrorCode.STUDYROOM_CLOSED, ErrorCode.STUDYROOM_ALREADY_STARTED, ErrorCode.TUTOR_CANNOT_JOIN_OWN_STUDYROOM,
      ErrorCode.ALREADY_JOINED_STUDYROOM, ErrorCode.STUDYROOM_FULL })
  public ResponseEntity<StudyRoomDTO> join(@PathVariable Long id, Authentication authentication) {
    // hasRole('STUDENT') guarantees this is a Student
    Student student = (Student) userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(studyRoomService.join(student, id));
  }

  @PostMapping("{id}/leave")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Leave a StudyRoom you joined before it starts, freeing your place")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.NOT_A_PARTICIPANT, ErrorCode.STUDYROOM_CLOSED,
      ErrorCode.STUDYROOM_ALREADY_STARTED })
  public ResponseEntity<StudyRoomDTO> leave(@PathVariable Long id, Authentication authentication) {
    // hasRole('STUDENT') guarantees this is a Student
    Student student = (Student) userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.leave(student, id));
  }

  @PostMapping("{id}/cancel")
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Cancel a StudyRoom you host before it starts, with an optional reason. "
      + "It leaves the calendar and never counts toward achievements")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACCESS_DENIED, ErrorCode.STUDYROOM_CLOSED,
      ErrorCode.STUDYROOM_ALREADY_STARTED })
  public ResponseEntity<StudyRoomDTO> cancel(@PathVariable Long id,
      @Valid @RequestBody(required = false) CancelStudyRoomRequestDTO request, Authentication authentication) {
    User tutor = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.ok(studyRoomService.cancel(tutor.getNumber(), id, request == null ? null : request.reason()));
  }

}
