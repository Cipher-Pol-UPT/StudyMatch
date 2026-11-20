package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Participation;
import com.studymatch.repositories.ParticipationRepository;

class ParticipationServiceTest {

  private ParticipationRepository participationRepository;
  private ParticipationService participationService;

  @BeforeEach
  void setUp() {
    participationRepository = mock(ParticipationRepository.class);
    participationService = new ParticipationService(participationRepository);
  }

  @Test
  @DisplayName("A student who joined the room gets their participation")
  void findsTheParticipation() {
    // Arrange
    Participation participation = mock(Participation.class);
    when(participationRepository.findByStudyRoom_IdAndStudent_Number(20L, 2L)).thenReturn(Optional.of(participation));

    // Act
    Participation found = participationService.getParticipation(20L, 2L);

    // Assert
    assertSame(participation, found);
  }

  @Test
  @DisplayName("A student who didn't join the room gets NOT_A_PARTICIPANT")
  void notAParticipant() {
    // Act - the repository is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> participationService.getParticipation(20L, 2L));

    // Assert
    assertEquals(ErrorCode.NOT_A_PARTICIPANT, e.getCode());
  }

}
