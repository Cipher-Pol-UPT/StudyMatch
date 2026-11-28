package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.DayOffRequestDTO;
import com.studymatch.dtos.DayOffResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.services.DayOffService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/academic-years/{startYear}/days-off")
@Tag(name = "Days without classes", description = "Holidays and exam seasons of an academic year (#336)")
public class DayOffController {

  private final DayOffService dayOffService;

  public DayOffController(DayOffService dayOffService) {
    this.dayOffService = dayOffService;
  }

  // any logged-in user: the timetable and the calendar show them
  @GetMapping
  @Operation(summary = "The year's days without classes, the earliest first")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<List<DayOffResponseDTO>> list(@PathVariable int startYear) {
    return ResponseEntity.ok(dayOffService.list(startYear).stream().map(DayOffResponseDTO::from).toList());
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Mark a day, or a range of days, of the current year as without classes")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACADEMIC_YEAR_NOT_CURRENT, ErrorCode.DAY_OFF_ENDS_BEFORE_START,
      ErrorCode.DAY_OFF_OUTSIDE_SEMESTERS, ErrorCode.DAY_OFF_OVERLAPS })
  public ResponseEntity<DayOffResponseDTO> add(@PathVariable int startYear, @Valid @RequestBody DayOffRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(DayOffResponseDTO.from(dayOffService.add(startYear, request)));
  }

  @DeleteMapping("{id}")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Give a day or range of the current year its classes back")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACADEMIC_YEAR_NOT_CURRENT })
  public ResponseEntity<Void> remove(@PathVariable int startYear, @PathVariable Long id) {
    dayOffService.remove(startYear, id);
    return ResponseEntity.noContent().build();
  }

}
