package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ConfirmAttendanceRequestDTO;
import com.studymatch.dtos.CreateStudyRoomRequestDTO;
import com.studymatch.dtos.ParticipantDTO;
import com.studymatch.dtos.StudyRoomDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.User;
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
  private final UserService userService;

  public StudyRoomController(StudyRoomService studyRoomService, UserService userService) {
    this.studyRoomService = studyRoomService;
    this.userService = userService;
  }

  @PostMapping
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Create a StudyRoom for a subject you tutor. ACCESS_DENIED if you don't tutor it")
  @ErrorCodes({ ErrorCode.STUDYROOM_IN_PAST, ErrorCode.STUDYROOM_ENDS_BEFORE_START, ErrorCode.STUDYROOM_OVERLAP })
  public ResponseEntity<StudyRoomDTO> create(@Valid @RequestBody CreateStudyRoomRequestDTO request,
      Authentication authentication) {
    User tutor = userService.getByInstitutionalEmail(authentication.getName());
    return ResponseEntity.status(HttpStatus.CREATED).body(studyRoomService.create(tutor.getNumber(), request));
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

}
