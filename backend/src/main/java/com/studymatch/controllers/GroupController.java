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
import com.studymatch.services.GroupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/groups/")
public class GroupController {

  private final GroupService groupService;

  public GroupController(GroupService groupService) {
    this.groupService = groupService;
  }

  // view analytics: every filter is optional, the year defaults to the current one
  @GetMapping("analytics")
  @PreAuthorize("hasRole('STAFF')")
  @Tag(name = "Analytics", description = "Aggregated indicators and charts about students and groups, for Staff")
  @Operation(summary = "The indicators and charts of an academic year, narrowed by subject edition, class or group. "
      + "Aggregated only, statute types are never shown")
  @ErrorCodes({ ErrorCode.NOT_FOUND })
  public ResponseEntity<AnalyticsDTO> getAnalytics(@RequestParam(required = false) Integer academicYear,
      @RequestParam(required = false) Long subjectEditionId, @RequestParam(required = false) String classSection,
      @RequestParam(required = false) Long groupId) {
    return ResponseEntity.ok(groupService.getAnalytics(academicYear, subjectEditionId, classSection, groupId));
  }

}
