package com.studymatch.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.AcademicYearPreviewDTO;
import com.studymatch.dtos.AcademicYearResponseDTO;
import com.studymatch.dtos.SemesterDatesDTO;
import com.studymatch.dtos.StartAcademicYearRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.AcademicYear;
import com.studymatch.services.AcademicYearService;
import com.studymatch.services.EnrolmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/academic-years/")
@Tag(name = "Academic years", description = "The newest registered year is the current one")
public class AcademicYearController {

  private final AcademicYearService academicYearService;
  private final EnrolmentService enrolmentService;

  public AcademicYearController(AcademicYearService academicYearService, EnrolmentService enrolmentService) {
    this.academicYearService = academicYearService;
    this.enrolmentService = enrolmentService;
  }

  // any logged-in user: Staff picks one for a subject edition, everyone else sees them on editions
  @GetMapping
  @Operation(summary = "Every academic year, newest first")
  public ResponseEntity<List<AcademicYearResponseDTO>> listAcademicYears() {
    return ResponseEntity.ok(academicYearService.listAcademicYears().stream().map(AcademicYearResponseDTO::from).toList());
  }

  // built here because the enrolments depend on the years, so the year service can't call them
  @GetMapping("next")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "The year Start Academic Year would create, and the current year's enrolments with no final grade")
  public ResponseEntity<AcademicYearPreviewDTO> previewNextYear() {
    Optional<AcademicYear> current = academicYearService.findCurrent();
    return ResponseEntity.ok(new AcademicYearPreviewDTO(academicYearService.nextStartYear(),
        current.map(AcademicYear::getStartYear).orElse(null),
        current.map(enrolmentService::listUnfinished).orElse(List.of())));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Start the next academic year with its semester dates: it becomes current and every student "
      + "retakes the personality test")
  @ErrorCodes({ ErrorCode.ACADEMIC_YEAR_NOT_NEXT, ErrorCode.SEMESTER_OUTSIDE_YEAR,
      ErrorCode.SEMESTER_ENDS_BEFORE_START, ErrorCode.SEMESTERS_OVERLAP })
  public ResponseEntity<AcademicYearResponseDTO> startNextYear(@Valid @RequestBody StartAcademicYearRequestDTO request) {
    AcademicYear academicYear = academicYearService.startNext(request.startYear(), request.semesters());
    return ResponseEntity.status(HttpStatus.CREATED).body(AcademicYearResponseDTO.from(academicYear));
  }

  @PutMapping("{startYear}/semesters")
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Change the current academic year's semester dates")
  @ErrorCodes({ ErrorCode.NOT_FOUND, ErrorCode.ACADEMIC_YEAR_NOT_CURRENT, ErrorCode.SEMESTER_OUTSIDE_YEAR,
      ErrorCode.SEMESTER_ENDS_BEFORE_START, ErrorCode.SEMESTERS_OVERLAP })
  public ResponseEntity<AcademicYearResponseDTO> editSemesters(@PathVariable int startYear,
      @Valid @RequestBody SemesterDatesDTO semesters) {
    return ResponseEntity.ok(AcademicYearResponseDTO.from(academicYearService.editSemesters(startYear, semesters)));
  }

}
