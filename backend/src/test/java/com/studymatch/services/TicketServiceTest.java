package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.studymatch.dtos.GuestTicketRequestDTO;
import com.studymatch.enums.TicketCategory;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Ticket;
import com.studymatch.repositories.TicketRepository;
import com.studymatch.utils.EmailSender;

class TicketServiceTest {

  private static final String FRONTEND = "http://localhost:5173";
  private static final String EMAIL = "guest@example.com";
  private static final String IP = "10.0.0.7";

  private TicketRepository ticketRepository;
  private EmailSender emailSender;
  private TicketService ticketService;

  @BeforeEach
  void setUp() {
    ticketRepository = mock(TicketRepository.class);
    emailSender = mock(EmailSender.class);
    ticketService = new TicketService(ticketRepository, emailSender, FRONTEND);
    when(ticketRepository.save(any(Ticket.class))).thenAnswer(call -> call.getArgument(0));
  }

  private GuestTicketRequestDTO request() {
    return new GuestTicketRequestDTO(EMAIL, TicketCategory.ACCOUNT, "locked out", "my account says blocked");
  }

  private void alreadySent(long byEmail, long byIp) {
    when(ticketRepository.countByGuestEmailIgnoreCaseAndCreatedAtAfter(eq(EMAIL), any(LocalDateTime.class)))
        .thenReturn(byEmail);
    when(ticketRepository.countByGuestIpAndCreatedAtAfter(eq(IP), any(LocalDateTime.class))).thenReturn(byIp);
  }

  private static String sha256(String text) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("A guest's 3rd ticket in the hour is saved, with only the hash of the token the email links to")
  void guestTicketSaved() throws Exception {
    // Arrange
    alreadySent(2, 9);
    ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);

    // Act
    ticketService.openAsGuest(request(), IP);

    // Assert
    verify(ticketRepository).save(saved.capture());
    verify(emailSender).sendSupportTicketLink(eq(EMAIL), eq("locked out"), link.capture());
    assertTrue(link.getValue().startsWith(FRONTEND + "/support/"));
    String token = link.getValue().substring((FRONTEND + "/support/").length());
    assertEquals(43, token.length());
    assertEquals(sha256(token), saved.getValue().getGuestTokenHash());
    assertNotEquals(token, saved.getValue().getGuestTokenHash());
    assertNull(saved.getValue().getAuthor());
    assertEquals(IP, saved.getValue().getGuestIp());
  }

  @Test
  @DisplayName("A 4th ticket from the same email in the hour is TOO_MANY_TICKETS with max 3, nothing saved or sent")
  void tooManyFromEmail() {
    // Arrange
    alreadySent(3, 3);

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class,
        () -> ticketService.openAsGuest(request(), IP));

    // Assert
    assertEquals(ErrorCode.TOO_MANY_TICKETS, error.getCode());
    assertEquals(Map.of("max", 3), error.getParams());
    verify(ticketRepository, never()).save(any(Ticket.class));
    verify(emailSender, never()).sendSupportTicketLink(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("An 11th ticket from the same ip in the hour is TOO_MANY_TICKETS with max 10, even from a new email")
  void tooManyFromIp() {
    // Arrange
    alreadySent(0, 10);

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class,
        () -> ticketService.openAsGuest(request(), IP));

    // Assert
    assertEquals(ErrorCode.TOO_MANY_TICKETS, error.getCode());
    assertEquals(Map.of("max", 10), error.getParams());
    verify(ticketRepository, never()).save(any(Ticket.class));
  }

  @Test
  @DisplayName("Opening a guest ticket waits on the email's lock, in lower case, and then the ip's")
  void guestTicketLocks() {
    // Arrange
    alreadySent(0, 0);

    // Act
    ticketService.openAsGuest(new GuestTicketRequestDTO("Guest@Example.com", TicketCategory.OTHER, "hi", "hello"), IP);

    // Assert
    verify(ticketRepository).lock("ticket-email:guest@example.com");
    verify(ticketRepository).lock("ticket-ip:" + IP);
  }

  @Test
  @DisplayName("A wrong link is NOT_FOUND")
  void wrongLink() throws Exception {
    // Arrange
    when(ticketRepository.findByGuestTokenHash(sha256("nope"))).thenReturn(Optional.empty());

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class, () -> ticketService.getGuest("nope"));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, error.getCode());
  }

}
