package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.BlockImpactDTO;
import com.studymatch.dtos.BlockedAccountDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Admin;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.UserRepository;
import com.studymatch.utils.EmailSender;

class BlockAccountTest {

  private static final Long NUMBER = 7L;

  private UserRepository userRepository;
  private StudyRoomService studyRoomService;
  private TutorApplicationService tutorApplicationService;
  private SubjectEditionService subjectEditionService;
  private NotificationService notificationService;
  private UserService userService;

  private Student tutor;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    studyRoomService = mock(StudyRoomService.class);
    tutorApplicationService = mock(TutorApplicationService.class);
    subjectEditionService = mock(SubjectEditionService.class);
    notificationService = mock(NotificationService.class);
    userService = new UserService(userRepository, mock(PasswordEncoder.class), mock(EmailSender.class),
        studyRoomService, tutorApplicationService, subjectEditionService, notificationService, "test.com");

    tutor = ModelTestData.student("Test Tutor");
    ReflectionTestUtils.setField(tutor, "number", NUMBER);
  }

  // block

  @Test
  @DisplayName("Blocking saves the account as blocked with its reason")
  void blockSavesAccount() {
    // Arrange
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));

    // Act
    BlockedAccountDTO blocked = userService.blockAccount(NUMBER, "test reason");

    // Assert
    assertEquals(AccountStatus.BLOCKED, blocked.account().accountStatus());
    assertEquals("test reason", tutor.getBlockReason());
    verify(userRepository).save(tutor);
  }

  @Test
  @DisplayName("Blocking cancels the tutor's upcoming rooms and pending applications and returns how many")
  void blockCancelsRoomsAndApplications() {
    // Arrange
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));
    when(studyRoomService.cancelUpcoming(NUMBER)).thenReturn(3);
    when(tutorApplicationService.cancelPending(NUMBER)).thenReturn(1);

    // Act
    BlockedAccountDTO blocked = userService.blockAccount(NUMBER, "test reason");

    // Assert
    assertEquals(3, blocked.cancelledStudyRooms());
    assertEquals(1, blocked.cancelledTutorApplications());
  }

  @Test
  @DisplayName("An admin isn't blocked and nothing of theirs is cancelled")
  void blockAdminCancelsNothing() {
    // Arrange
    Admin admin = new Admin("admin@example.com", "admin@test.com", "Test Admin", "hash");
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(admin));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.blockAccount(NUMBER, "test reason"));

    // Assert
    assertEquals(ErrorCode.ADMIN_CANNOT_BE_BLOCKED, e.getCode());
    verify(userRepository, never()).save(any());
    verifyNoInteractions(studyRoomService, tutorApplicationService);
  }

  @Test
  @DisplayName("Blocking an already blocked account cancels nothing again")
  void blockTwiceCancelsNothing() {
    // Arrange
    tutor.block("first reason");
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.blockAccount(NUMBER, "test reason"));

    // Assert
    assertEquals(ErrorCode.ACCOUNT_ALREADY_BLOCKED, e.getCode());
    verifyNoInteractions(studyRoomService, tutorApplicationService);
  }

  @Test
  @DisplayName("An unknown number is NOT_FOUND")
  void blockUnknownAccount() {
    // Act - findById is empty by default
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> userService.blockAccount(NUMBER, "test reason"));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  // impact

  @Test
  @DisplayName("The impact has the rooms and applications that would be cancelled, without blocking")
  void impactCountsWithoutBlocking() {
    // Arrange
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));
    when(studyRoomService.countUpcoming(NUMBER)).thenReturn(2);
    when(tutorApplicationService.countPending(NUMBER)).thenReturn(1);

    // Act
    BlockImpactDTO impact = userService.getBlockImpact(NUMBER);

    // Assert
    assertEquals(NUMBER, impact.account().number());
    assertEquals(Role.STUDENT, impact.account().role());
    assertEquals(2, impact.upcomingStudyRooms());
    assertEquals(1, impact.pendingTutorApplications());
    assertTrue(impact.currentEditions().isEmpty());
    assertEquals(AccountStatus.ACTIVE, tutor.getAccountStatus());
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("A teacher's impact lists their editions this year")
  void impactListsTeacherEditions() {
    // Arrange
    Teacher teacher = ModelTestData.teacher();
    List<SubjectEditionResponseDTO> editions = List.of(mock(SubjectEditionResponseDTO.class));
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(teacher));
    when(subjectEditionService.listCurrentTaughtBy(NUMBER)).thenReturn(editions);

    // Act
    BlockImpactDTO impact = userService.getBlockImpact(NUMBER);

    // Assert
    assertSame(editions, impact.currentEditions());
  }

  @Test
  @DisplayName("The impact of an admin is refused straight away")
  void impactOfAdminIsRefused() {
    // Arrange
    Admin admin = new Admin("admin@example.com", "admin@test.com", "Test Admin", "hash");
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(admin));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> userService.getBlockImpact(NUMBER));

    // Assert
    assertEquals(ErrorCode.ADMIN_CANNOT_BE_BLOCKED, e.getCode());
  }

  @Test
  @DisplayName("The impact of a blocked account is refused straight away")
  void impactOfBlockedAccountIsRefused() {
    // Arrange
    tutor.block("first reason");
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> userService.getBlockImpact(NUMBER));

    // Assert
    assertEquals(ErrorCode.ACCOUNT_ALREADY_BLOCKED, e.getCode());
  }

  // unblock

  @Test
  @DisplayName("Unblocking makes the account active again and tells the user")
  void unblockNotifiesUser() {
    // Arrange
    tutor.block("first reason");
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));
    when(userRepository.save(tutor)).thenReturn(tutor);

    // Act
    userService.unblockAccount(NUMBER);

    // Assert
    assertEquals(AccountStatus.ACTIVE, tutor.getAccountStatus());
    verify(notificationService).notify(tutor, NotificationType.ACCOUNT_UNBLOCKED, null, null, Map.of());
  }

  @Test
  @DisplayName("Unblocking an account that isn't blocked tells nobody")
  void unblockActiveAccountNotifiesNobody() {
    // Arrange
    when(userRepository.findById(NUMBER)).thenReturn(Optional.of(tutor));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> userService.unblockAccount(NUMBER));

    // Assert
    assertEquals(ErrorCode.ACCOUNT_NOT_BLOCKED, e.getCode());
    verifyNoInteractions(notificationService);
  }

}
