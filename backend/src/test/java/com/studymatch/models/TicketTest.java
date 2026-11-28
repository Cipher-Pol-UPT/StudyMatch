package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.enums.Role;
import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class TicketTest {

  private Ticket ticket(TicketCategory category) {
    return new Ticket(ModelTestData.student("Ana"), category, "  can't log in  ", "  it says blocked  ");
  }

  @Test
  @DisplayName("A new ticket is Open, with its subject and description stripped")
  void newTicket() {
    // Act
    Ticket ticket = ticket(TicketCategory.TECHNICAL);

    // Assert
    assertEquals(TicketStatus.OPEN, ticket.getStatus());
    assertEquals("can't log in", ticket.getSubject());
    assertEquals("it says blocked", ticket.getDescription());
    assertEquals(ticket.getCreatedAt(), ticket.getUpdatedAt());
  }

  @Test
  @DisplayName("A guest ticket has the email, ip and token hash and no author, and starts Open")
  void guestTicket() {
    // Act
    Ticket ticket = Ticket.forGuest("  guest@example.com ", "10.0.0.7", "hash", TicketCategory.ACCOUNT, "help",
        "locked out");

    // Assert
    assertTrue(ticket.isFromGuest());
    assertNull(ticket.getAuthor());
    assertEquals("guest@example.com", ticket.getGuestEmail());
    assertEquals("10.0.0.7", ticket.getGuestIp());
    assertEquals("hash", ticket.getGuestTokenHash());
    assertEquals(TicketStatus.OPEN, ticket.getStatus());
  }

  @Test
  @DisplayName("A ticket with an author isn't from a guest")
  void authorTicket() {
    // Act + Assert
    assertFalse(ticket(TicketCategory.OTHER).isFromGuest());
  }

  @Test
  @DisplayName("Replacing the guest token keeps only the new hash")
  void replaceGuestToken() {
    // Arrange
    Ticket ticket = Ticket.forGuest("guest@example.com", "10.0.0.7", "old", TicketCategory.OTHER, "help", "hi");

    // Act
    ticket.replaceGuestToken("new");

    // Assert
    assertEquals("new", ticket.getGuestTokenHash());
  }

  @Test
  @DisplayName("An Account ticket goes to the Admin queue")
  void accountQueue() {
    // Act + Assert
    assertEquals(Role.ADMIN, ticket(TicketCategory.ACCOUNT).getQueue());
  }

  @ParameterizedTest
  @EnumSource(value = TicketCategory.class, names = "ACCOUNT", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Every other category goes to the Staff queue")
  void staffQueue(TicketCategory category) {
    // Act + Assert
    assertEquals(Role.STAFF, ticket(category).getQueue());
  }

  @Test
  @DisplayName("The author writing back on a Resolved ticket opens it again")
  void replyReopens() {
    // Arrange
    Ticket ticket = ticket(TicketCategory.OTHER);
    ReflectionTestUtils.setField(ticket, "status", TicketStatus.RESOLVED);

    // Act
    ticket.authorReplied();

    // Assert
    assertEquals(TicketStatus.OPEN, ticket.getStatus());
  }

  @Test
  @DisplayName("The author writing back on a ticket In progress leaves it In progress")
  void replyInProgress() {
    // Arrange
    Ticket ticket = ticket(TicketCategory.OTHER);
    ReflectionTestUtils.setField(ticket, "status", TicketStatus.IN_PROGRESS);

    // Act
    ticket.authorReplied();

    // Assert
    assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
  }

  @Test
  @DisplayName("A reply moves the ticket's last activity")
  void replyTouches() {
    // Arrange
    Ticket ticket = ticket(TicketCategory.OTHER);
    LocalDateTime before = LocalDateTime.of(2026, 1, 1, 9, 0);
    ReflectionTestUtils.setField(ticket, "updatedAt", before);

    // Act
    ticket.authorReplied();

    // Assert
    assertTrue(ticket.getUpdatedAt().isAfter(before));
  }

  @ParameterizedTest
  @EnumSource(TicketStatus.class)
  @DisplayName("The queue answering sets any ticket In progress, a Resolved one too, and moves its last activity")
  void answered(TicketStatus status) {
    // Arrange
    Ticket ticket = ticket(TicketCategory.OTHER);
    LocalDateTime before = LocalDateTime.of(2026, 1, 1, 9, 0);
    ReflectionTestUtils.setField(ticket, "status", status);
    ReflectionTestUtils.setField(ticket, "updatedAt", before);

    // Act
    ticket.answered();

    // Assert
    assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
    assertTrue(ticket.getUpdatedAt().isAfter(before));
  }

  @ParameterizedTest
  @EnumSource(value = TicketStatus.class, names = "RESOLVED", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("Resolving an Open or In progress ticket sets it Resolved")
  void resolve(TicketStatus status) {
    // Arrange
    Ticket ticket = ticket(TicketCategory.OTHER);
    ReflectionTestUtils.setField(ticket, "status", status);

    // Act
    ticket.resolve();

    // Assert
    assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
  }

  @Test
  @DisplayName("Resolving a Resolved ticket is TICKET_ALREADY_RESOLVED")
  void resolveTwice() {
    // Arrange
    Ticket ticket = ticket(TicketCategory.OTHER);
    ticket.resolve();

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class, ticket::resolve);

    // Assert
    assertEquals(ErrorCode.TICKET_ALREADY_RESOLVED, error.getCode());
  }

  @Test
  @DisplayName("The Admin queue is only Account, the Staff queue the other 3, and the other roles answer none")
  void inQueue() {
    // Act + Assert
    assertEquals(List.of(TicketCategory.ACCOUNT), TicketCategory.inQueue(Role.ADMIN));
    assertEquals(List.of(TicketCategory.GRADES_AND_ENROLMENT, TicketCategory.TECHNICAL, TicketCategory.OTHER),
        TicketCategory.inQueue(Role.STAFF));
    assertTrue(TicketCategory.inQueue(Role.STUDENT).isEmpty());
    assertTrue(TicketCategory.inQueue(Role.TEACHER).isEmpty());
  }

}
