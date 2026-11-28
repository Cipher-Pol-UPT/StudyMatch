package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.dtos.UnavailableSlotDTO;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.repositories.UnavailableSlotRepository;
import com.studymatch.services.UnavailableSlotService;

// the same teacher saving from two tabs at once. real commits, so it cleans up after itself
@SpringBootTest
@ActiveProfiles("test")
class UnavailabilityConcurrencyIntegrationTest {

  // far in the future so it's the current year while the test runs
  private static final int CURRENT_YEAR = 2097;

  // long enough for the other thread to reach the lock and be seen waiting
  private static final long WAITING_MILLIS = 700;

  @Autowired
  private UnavailableSlotService unavailableSlotService;

  @Autowired
  private UnavailableSlotRepository unavailableSlotRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
  private TransactionTemplate transaction;

  private AcademicYear year;
  private Teacher ana;

  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
    year = academicYearRepository.save(new AcademicYear(CURRENT_YEAR));
    ana = teacherRepository.save(new Teacher("it.concurrent.slots@example.com",
        "it.concurrent.slots@studymatch.com", "IT Concurrent Slots", "hash"));
  }

  @AfterEach
  void cleanUp() {
    otherThread.shutdownNow();
    transaction.executeWithoutResult(status -> unavailableSlotRepository.deleteByTeacherAndYear(ana.getNumber(), year));
    teacherRepository.delete(ana);
    academicYearRepository.delete(year);
  }

  private void save(DayOfWeek day) {
    unavailableSlotService.replaceCurrent(ana.getNumber(),
        List.of(new UnavailableSlotDTO(day, LocalTime.of(8, 0)), new UnavailableSlotDTO(day, LocalTime.of(8, 30))));
  }

  @Test
  @DisplayName("Two saves at once: the second waits for the first, then replaces its slots instead of failing")
  void secondSaveWaitsThenReplaces() throws Exception {
    // Arrange
    Future<?>[] second = new Future<?>[1];

    // Act - the first is saved but not committed when the second starts
    transaction.executeWithoutResult(status -> {
      save(DayOfWeek.MONDAY);
      second[0] = otherThread.submit(() -> save(DayOfWeek.TUESDAY));
      assertThrows(TimeoutException.class, () -> second[0].get(WAITING_MILLIS, TimeUnit.MILLISECONDS));
    });
    second[0].get(5, TimeUnit.SECONDS);

    // Assert
    List<UnavailableSlotDTO> saved = unavailableSlotService.listCurrent(ana.getNumber()).stream()
        .map(UnavailableSlotDTO::from).toList();
    assertEquals(List.of(new UnavailableSlotDTO(DayOfWeek.TUESDAY, LocalTime.of(8, 0)),
        new UnavailableSlotDTO(DayOfWeek.TUESDAY, LocalTime.of(8, 30))), saved);
  }

}
