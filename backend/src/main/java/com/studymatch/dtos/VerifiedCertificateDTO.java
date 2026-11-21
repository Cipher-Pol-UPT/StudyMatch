package com.studymatch.dtos;

import java.time.LocalDate;
import java.util.List;

import com.studymatch.models.Student;

// what the public verify page shows, record is true for a certificate with several achievements
public record VerifiedCertificateDTO(boolean record, String studentName, Long studentNumber, String degree,
    LocalDate issuedAt, List<VerifiedAchievementDTO> achievements) {

  public static VerifiedCertificateDTO of(boolean record, Student student, LocalDate issuedAt,
      List<VerifiedAchievementDTO> achievements) {
    String degree = student.getDegree() == null ? null : student.getDegree().getName();
    return new VerifiedCertificateDTO(record, student.getName(), student.getNumber(), degree, issuedAt, achievements);
  }

}
