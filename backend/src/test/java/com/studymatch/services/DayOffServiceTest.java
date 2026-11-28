package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.DayOff;
import com.studymatch.repositories.DayOffRepository;

class DayOffServiceTest {

  @Test
  @DisplayName("The dates without classes are every day of each range, in order, each once")
  void datesWithoutClasses() {
    // Arrange
    DayOffRepository repository = mock(DayOffRepository.class);
    AcademicYear year = new AcademicYear(2026);
    when(repository.findByAcademicYearOrderByFirstDay(year)).thenReturn(List.of(
        new DayOff(year, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5), "Republic Day"),
        new DayOff(year, LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 1), "New Year")));
    DayOffService service = new DayOffService(repository, mock(AcademicYearService.class));

    // Act + Assert
    assertEquals(List.of(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31),
        LocalDate.of(2027, 1, 1)), List.copyOf(service.datesWithoutClasses(year)));
  }

}
