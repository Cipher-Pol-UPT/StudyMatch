package com.studymatch.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.AnalyticsDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.services.AnalyticsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/analytics/")
@Tag(name = "Analytics", description = "Aggregated indicators and charts about students and groups, for Staff")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  // view analytics: every filter is optional, the year defaults to the current one
  @GetMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "The indicators and charts of an academic year, narrowed by subject edition, class or group. "
      + "Aggregated only, statute types are never shown")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<AnalyticsDTO> getAnalytics(@RequestParam(required = false) Integer academicYear,
      @RequestParam(required = false) Long subjectEditionId, @RequestParam(required = false) String classSection,
      @RequestParam(required = false) Long groupId) {
    return ResponseEntity.ok(analyticsService.getAnalytics(academicYear, subjectEditionId, classSection, groupId));
  }

}
