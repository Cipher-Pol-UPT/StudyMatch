package com.studymatch.models;

import java.time.LocalDate;

import jakarta.persistence.Embeddable;

// the tutor's figures the first time a certificate is issued, so the paper and the verify page never disagree
@Embeddable
public record CertificateEvidence(Long roomsHeld, Long studentsTutored, Double averageRating, String subject,
    LocalDate recordedAt) {
}
