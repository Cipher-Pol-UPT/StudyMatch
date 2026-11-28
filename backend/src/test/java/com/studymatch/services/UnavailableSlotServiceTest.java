package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.studymatch.dtos.UnavailableSlotDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Teacher;
import com.studymatch.models.UnavailableSlot;
import com.studymatch.repositories.UnavailableSlotRepository;

class UnavailableSlotServiceTest {

  private static final Long ANA_NUMBER = 7L;

  private UnavailableSlotRepository unavailableSlotRepository;
  private TeacherService teacherService;
  private AcademicYearService academicYearService;
  private UnavailableSlotService unavailableSlotService;

  private Teacher ana;
  private AcademicYear year2026;

  @BeforeEach
  void setUp() {
    unavailableSlotRepository = mock(UnavailableSlotRepository.class);
    teacherService = mock(TeacherService.class);
    academicYearService = mock(AcademicYearService.class);
    unavailableSlotService = new UnavailableSlotService(unavailableSlotRepository, teacherService,
        academicYearService);

    ana = new Teacher("ana@example.com", "ana@studymatch.com", "Ana", "hash");
    year2026 = new AcademicYear(2026);
    when(teacherService.getLockedTeacher(ANA_NUMBER)).thenReturn(ana);
    when(academicYearService.findCurrent()).thenReturn(Optional.of(year2026));
    // saveAll hands back what it got, like the real one
    when(unavailableSlotRepository.saveAll(any())).thenAnswer(call -> {
      List<UnavailableSlot> saved = new ArrayList<>();
      call.<Iterable<UnavailableSlot>>getArgument(0).forEach(saved::add);
      return saved;
    });
  }

  private UnavailableSlot slot(DayOfWeek day, String start) {
    return new UnavailableSlot(ana, year2026, day, LocalTime.parse(start));
  }

  private static UnavailableSlotDTO dto(DayOfWeek day, String start) {
    return new UnavailableSlotDTO(day, LocalTime.parse(start));
  }

  private static List<UnavailableSlotDTO> dtos(List<UnavailableSlot> slots) {
    return slots.stream().map(UnavailableSlotDTO::from).toList();
  }

  // list

  @Test
  @DisplayName("Listing sorts the slots by weekday then time, not by the day's name")
  void listSortsByDayThenTime() {
    // Arrange
    when(unavailableSlotRepository.findByTeacher_NumberAndAcademicYear(ANA_NUMBER, year2026)).thenReturn(List.of(
        slot(DayOfWeek.FRIDAY, "08:00"), slot(DayOfWeek.MONDAY, "10:00"), slot(DayOfWeek.MONDAY, "08:30")));

    // Act
    List<UnavailableSlot> slots = unavailableSlotService.listCurrent(ANA_NUMBER);

    // Assert
    assertEquals(List.of(dto(DayOfWeek.MONDAY, "08:30"), dto(DayOfWeek.MONDAY, "10:00"),
        dto(DayOfWeek.FRIDAY, "08:00")), dtos(slots));
  }

  @Test
  @DisplayName("With no academic year yet the list is empty, so the teacher is available all week")
  void listWithoutYear() {
    // Arrange
    when(academicYearService.findCurrent()).thenReturn(Optional.empty());

    // Act + Assert
    assertTrue(unavailableSlotService.listCurrent(ANA_NUMBER).isEmpty());
    verifyNoInteractions(unavailableSlotRepository);
  }

  // replace

  @Test
  @DisplayName("Saving locks the teacher, deletes this year's old slots and then saves the new ones, sorted")
  void replaceDeletesThenSaves() {
    // Act
    List<UnavailableSlot> slots = unavailableSlotService.replaceCurrent(ANA_NUMBER,
        List.of(dto(DayOfWeek.TUESDAY, "14:00"), dto(DayOfWeek.MONDAY, "08:00")));

    // Assert
    InOrder order = inOrder(teacherService, unavailableSlotRepository);
    order.verify(teacherService).getLockedTeacher(ANA_NUMBER);
    order.verify(unavailableSlotRepository).deleteByTeacherAndYear(ANA_NUMBER, year2026);
    order.verify(unavailableSlotRepository).saveAll(any());
    assertEquals(List.of(dto(DayOfWeek.MONDAY, "08:00"), dto(DayOfWeek.TUESDAY, "14:00")), dtos(slots));
    assertSame(ana, slots.get(0).getTeacher());
    assertSame(year2026, slots.get(0).getAcademicYear());
  }

  @Test
  @DisplayName("The same slot sent twice is saved once")
  void replaceDeduplicates() {
    // Act
    List<UnavailableSlot> slots = unavailableSlotService.replaceCurrent(ANA_NUMBER,
        List.of(dto(DayOfWeek.MONDAY, "08:00"), dto(DayOfWeek.MONDAY, "08:00")));

    // Assert
    assertEquals(List.of(dto(DayOfWeek.MONDAY, "08:00")), dtos(slots));
  }

  @Test
  @DisplayName("Saving an empty list clears the slots (boundary)")
  void replaceWithNothing() {
    // Act
    List<UnavailableSlot> slots = unavailableSlotService.replaceCurrent(ANA_NUMBER, List.of());

    // Assert
    assertTrue(slots.isEmpty());
    verify(unavailableSlotRepository).deleteByTeacherAndYear(ANA_NUMBER, year2026);
  }

  @Test
  @DisplayName("One slot off the grid is INVALID_SLOT and the old slots stay")
  void replaceWithInvalidSlot() {
    // Arrange
    List<UnavailableSlotDTO> slots = List.of(dto(DayOfWeek.MONDAY, "08:00"), dto(DayOfWeek.SATURDAY, "08:00"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> unavailableSlotService.replaceCurrent(ANA_NUMBER, slots));

    // Assert
    assertSame(ErrorCode.INVALID_SLOT, e.getCode());
    verify(unavailableSlotRepository, never()).deleteByTeacherAndYear(any(), any());
    verify(unavailableSlotRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Saving with no academic year yet is NO_CURRENT_ACADEMIC_YEAR")
  void replaceWithoutYear() {
    // Arrange
    when(academicYearService.findCurrent()).thenReturn(Optional.empty());
    List<UnavailableSlotDTO> slots = List.of(dto(DayOfWeek.MONDAY, "08:00"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> unavailableSlotService.replaceCurrent(ANA_NUMBER, slots));

    // Assert
    assertSame(ErrorCode.NO_CURRENT_ACADEMIC_YEAR, e.getCode());
    verifyNoInteractions(unavailableSlotRepository);
  }

}
