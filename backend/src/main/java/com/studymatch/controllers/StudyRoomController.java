package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.CreateStudyRoomRequestDTO;
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

}
