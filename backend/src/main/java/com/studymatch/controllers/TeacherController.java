package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.TeacherSummaryDTO;
import com.studymatch.services.TeacherService;

@RestController
@RequestMapping("/teachers/")
public class TeacherController {

  private final TeacherService teacherService;

  public TeacherController(TeacherService teacherService) {
    this.teacherService = teacherService;
  }

  // Staff picks the teachers of a subject edition from this list
  @GetMapping
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<List<TeacherSummaryDTO>> listTeachers() {
    return ResponseEntity.ok(teacherService.listTeachers().stream().map(TeacherSummaryDTO::from).toList());
  }

}
