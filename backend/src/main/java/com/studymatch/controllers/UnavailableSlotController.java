package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.UnavailabilityRequestDTO;
import com.studymatch.dtos.UnavailableSlotDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.UnavailableSlot;
import com.studymatch.services.UnavailableSlotService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/teachers/me/unavailability")
@PreAuthorize("hasRole('TEACHER')")
@Tag(name = "Teacher unavailability", description = "The weekly 30-minute slots a teacher can't teach (GR13)")
public class UnavailableSlotController {

  private final UnavailableSlotService unavailableSlotService;
  private final UserService userService;

  public UnavailableSlotController(UnavailableSlotService unavailableSlotService, UserService userService) {
    this.unavailableSlotService = unavailableSlotService;
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "Your slots this academic year, by day and time. Empty means available all week")
  public ResponseEntity<List<UnavailableSlotDTO>> getMyUnavailability(Authentication authentication) {
    Long teacherNumber = userService.getByInstitutionalEmail(authentication.getName()).getNumber();
    return ResponseEntity.ok(toDTOs(unavailableSlotService.listCurrent(teacherNumber)));
  }

  @PutMapping
  @Operation(summary = "Replace your slots this academic year. Each is 30 minutes, Monday to Friday, "
      + "starting 8:00 to 19:30 on the hour or half hour. An empty list clears them")
  @ErrorCodes({ ErrorCode.INVALID_SLOT, ErrorCode.NO_CURRENT_ACADEMIC_YEAR })
  public ResponseEntity<List<UnavailableSlotDTO>> updateMyUnavailability(
      @Valid @RequestBody UnavailabilityRequestDTO request, Authentication authentication) {
    Long teacherNumber = userService.getByInstitutionalEmail(authentication.getName()).getNumber();
    return ResponseEntity.ok(toDTOs(unavailableSlotService.replaceCurrent(teacherNumber, request.slots())));
  }

  private static List<UnavailableSlotDTO> toDTOs(List<UnavailableSlot> slots) {
    return slots.stream().map(UnavailableSlotDTO::from).toList();
  }

}
