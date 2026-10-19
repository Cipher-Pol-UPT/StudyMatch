package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.repositories.AcademicYearRepository;

class AcademicYearServiceTest {

  private AcademicYearRepository academicYearRepository;
  private AcademicYearService academicYearService;

  @BeforeEach
  void setUp() {
    academicYearRepository = mock(AcademicYearRepository.class);
    academicYearService = new AcademicYearService(academicYearRepository);
  }

  @Test
  @DisplayName("Listing returns the years in the order the repository sorted them (newest first)")
  void listReturnsSortedYears() {
    // Arrange
    List<AcademicYear> sorted = List.of(new AcademicYear(2027), new AcademicYear(2026));
    when(academicYearRepository.findAllByOrderByStartYearDesc()).thenReturn(sorted);

    // Act + Assert
    assertEquals(sorted, academicYearService.listAcademicYears());
  }

  @Test
  @DisplayName("Getting a registered year returns it")
  void getAcademicYear() {
    // Arrange
    AcademicYear year2026 = new AcademicYear(2026);
    when(academicYearRepository.findById(2026)).thenReturn(Optional.of(year2026));

    // Act + Assert
    assertSame(year2026, academicYearService.getAcademicYear(2026));
  }

  @Test
  @DisplayName("A year that isn't registered gets NOT_FOUND")
  void unknownYearIsNotFound() {
    // Arrange
    when(academicYearRepository.findById(2099)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> academicYearService.getAcademicYear(2099));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
