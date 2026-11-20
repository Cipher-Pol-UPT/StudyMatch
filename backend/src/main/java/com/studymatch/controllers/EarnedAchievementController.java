package com.studymatch.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ExportCertificateRequestDTO;
import com.studymatch.dtos.VerifiedCertificateDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Student;
import com.studymatch.services.EarnedAchievementService;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/achievements/")
public class EarnedAchievementController {

  private final EarnedAchievementService earnedAchievementService;
  private final UserService userService;

  public EarnedAchievementController(EarnedAchievementService earnedAchievementService, UserService userService) {
    this.earnedAchievementService = earnedAchievementService;
    this.userService = userService;
  }

  @PostMapping(value = "certificate", produces = MediaType.APPLICATION_PDF_VALUE)
  @PreAuthorize("hasRole('STUDENT')")
  @Operation(summary = "Your certificate as a PDF: one achievement on its own page, several on one record. "
      + "Each has a QR code to its public verify page")
  @ErrorCodes(ErrorCode.ACHIEVEMENT_NOT_EARNED)
  public ResponseEntity<byte[]> exportCertificate(@Valid @RequestBody ExportCertificateRequestDTO request,
      Authentication authentication) {
    // hasRole('STUDENT') above, so the account is a Student
    Student student = (Student) userService.getByInstitutionalEmail(authentication.getName());
    String fileName = request.achievements().size() == 1 ? "certificate.pdf" : "achievements.pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(earnedAchievementService.exportCertificate(student, request));
  }

  @GetMapping("certificates/{code}")
  @Operation(summary = "Public, no login: who earned the achievements on a certificate and when, for its QR code")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<VerifiedCertificateDTO> verifyCertificate(@PathVariable String code) {
    return ResponseEntity.ok(earnedAchievementService.verifyCertificate(code));
  }

}
