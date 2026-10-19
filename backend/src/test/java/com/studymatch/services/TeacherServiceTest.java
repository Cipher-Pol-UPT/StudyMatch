package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.TeacherRepository;

class TeacherServiceTest {

  private TeacherRepository teacherRepository;
  private TeacherService teacherService;

  private Teacher ana;
  private Teacher rui;

  @BeforeEach
  void setUp() {
    teacherRepository = mock(TeacherRepository.class);
    teacherService = new TeacherService(teacherRepository);

    ana = new Teacher("ana@example.com", "ana@studymatch.com", "Ana", "hash");
    rui = new Teacher("rui@example.com", "rui@studymatch.com", "Rui", "hash");
  }

  // list teachers

  @Test
  @DisplayName("Listing returns the teachers in the order the repository sorted them (by name)")
  void listReturnsSortedTeachers() {
    // Arrange
    when(teacherRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ana, rui));

    // Act + Assert
    assertEquals(List.of(ana, rui), teacherService.listTeachers());
  }

  // get teachers by number

  @Test
  @DisplayName("Getting teachers by number returns them")
  void getTeachersByNumber() {
    // Arrange
    when(teacherRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(ana, rui));

    // Act + Assert
    assertEquals(Set.of(ana, rui), teacherService.getTeachers(List.of(1L, 2L)));
  }

  @Test
  @DisplayName("The same number twice counts once")
  void repeatedNumberCountsOnce() {
    // Arrange
    when(teacherRepository.findAllById(Set.of(1L))).thenReturn(List.of(ana));

    // Act + Assert
    assertEquals(Set.of(ana), teacherService.getTeachers(List.of(1L, 1L)));
  }

  @Test
  @DisplayName("No numbers gives no teachers (whoever needs at least one decides what that means)")
  void noNumbersIsEmpty() {
    // Arrange
    when(teacherRepository.findAllById(Set.of())).thenReturn(List.of());

    // Act + Assert
    assertEquals(Set.of(), teacherService.getTeachers(List.of()));
  }

  @Test
  @DisplayName("A number with no teacher gets NOT_FOUND")
  void unknownNumberIsNotFound() {
    // Arrange - only 1 exists
    when(teacherRepository.findAllById(Set.of(1L, 99L))).thenReturn(List.of(ana));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> teacherService.getTeachers(List.of(1L, 99L)));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
