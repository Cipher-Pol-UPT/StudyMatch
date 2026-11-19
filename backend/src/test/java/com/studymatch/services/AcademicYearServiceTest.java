package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.studymatch.events.AcademicYearStartedEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.repositories.AcademicYearRepository;

class AcademicYearServiceTest {

  private AcademicYearRepository academicYearRepository;
  private ApplicationEventPublisher eventPublisher;
  private AcademicYearService academicYearService;

  @BeforeEach
  void setUp() {
    academicYearRepository = mock(AcademicYearRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    academicYearService = new AcademicYearService(academicYearRepository, eventPublisher);
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

  // current year

  @Test
  @DisplayName("The newest registered year is the current one")
  void newestYearIsCurrent() {
    // Arrange
    when(academicYearRepository.findFirstByOrderByStartYearDesc()).thenReturn(Optional.of(new AcademicYear(2027)));

    // Act + Assert - another object for the same year still counts
    assertTrue(academicYearService.isCurrent(new AcademicYear(2027)));
  }

  @Test
  @DisplayName("An older year isn't current")
  void olderYearIsNotCurrent() {
    // Arrange
    when(academicYearRepository.findFirstByOrderByStartYearDesc()).thenReturn(Optional.of(new AcademicYear(2027)));

    // Act + Assert
    assertFalse(academicYearService.isCurrent(new AcademicYear(2026)));
  }

  @Test
  @DisplayName("With no years registered nothing is current")
  void noYearsMeansNoCurrent() {
    // Act + Assert - the mock returns an empty Optional by default
    assertTrue(academicYearService.findCurrent().isEmpty());
    assertFalse(academicYearService.isCurrent(new AcademicYear(2026)));
  }

  // start academic year

  @Test
  @DisplayName("The next year is the one after the current year")
  void nextYearFollowsCurrent() {
    // Arrange
    when(academicYearRepository.findFirstByOrderByStartYearDesc()).thenReturn(Optional.of(new AcademicYear(2027)));

    // Act + Assert
    assertEquals(2028, academicYearService.nextStartYear());
  }

  @Test
  @DisplayName("With no years registered the next year is the one today falls in")
  void firstYearComesFromToday() {
    // Act + Assert - the mock returns an empty Optional by default
    assertEquals(AcademicYear.startYearOf(LocalDate.now()), academicYearService.nextStartYear());
  }

  @Test
  @DisplayName("Starting the next year saves it and tells the listeners it started")
  void startNextSavesAndPublishes() {
    // Arrange
    when(academicYearRepository.findFirstByOrderByStartYearDesc()).thenReturn(Optional.of(new AcademicYear(2027)));
    when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    AcademicYear started = academicYearService.startNext(2028);

    // Assert
    assertEquals(2028, started.getStartYear());
    verify(academicYearRepository).save(started);
    verify(eventPublisher).publishEvent(new AcademicYearStartedEvent(started));
  }

  @Test
  @DisplayName("Confirming the current year (a second click) gets ACADEMIC_YEAR_NOT_NEXT and changes nothing")
  void currentYearIsNotNext() {
    // Arrange
    when(academicYearRepository.findFirstByOrderByStartYearDesc()).thenReturn(Optional.of(new AcademicYear(2028)));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> academicYearService.startNext(2028));

    // Assert
    assertEquals(ErrorCode.ACADEMIC_YEAR_NOT_NEXT, e.getCode());
    verify(academicYearRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  @DisplayName("Skipping a year gets ACADEMIC_YEAR_NOT_NEXT and changes nothing")
  void skippedYearIsNotNext() {
    // Arrange
    when(academicYearRepository.findFirstByOrderByStartYearDesc()).thenReturn(Optional.of(new AcademicYear(2027)));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> academicYearService.startNext(2029));

    // Assert
    assertEquals(ErrorCode.ACADEMIC_YEAR_NOT_NEXT, e.getCode());
    verify(academicYearRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

}
