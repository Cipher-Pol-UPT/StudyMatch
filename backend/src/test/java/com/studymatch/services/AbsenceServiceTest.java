package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.models.Enrolment;
import com.studymatch.models.ModelTestData;
import com.studymatch.repositories.AbsenceRepository;

class AbsenceServiceTest {

  private AbsenceRepository absenceRepository;
  private AbsenceService absenceService;

  @BeforeEach
  void setUp() {
    absenceRepository = mock(AbsenceRepository.class);
    absenceService = new AbsenceService(absenceRepository);
  }

  @Test
  @DisplayName("Counts only the unjustified absences of an enrolment")
  void countsUnjustifiedAbsences() {
    // Arrange
    Enrolment enrolment = ModelTestData.enrolment(ModelTestData.student("Test Student"));
    when(absenceRepository.countByEnrolmentAndJustifiedFalse(enrolment)).thenReturn(3L);

    // Act
    long absences = absenceService.countUnjustifiedAbsences(enrolment);

    // Assert
    assertEquals(3, absences);
  }

}
