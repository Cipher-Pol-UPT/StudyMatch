package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.GuestTicketRequestDTO;
import com.studymatch.dtos.QueueDTO;
import com.studymatch.dtos.QueueTicketDTO;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.Role;
import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.Ticket;
import com.studymatch.repositories.TicketRepository;
import com.studymatch.utils.EmailSender;

class TicketServiceTest {

  private static final String FRONTEND = "http://localhost:5173";
  private static final String EMAIL = "guest@example.com";
  private static final String IP = "10.0.0.7";
  private static final Long TICKET = 7L;

  private TicketRepository ticketRepository;
  private NotificationService notificationService;
  private EmailSender emailSender;
  private TicketService ticketService;

  @BeforeEach
  void setUp() {
    ticketRepository = mock(TicketRepository.class);
    notificationService = mock(NotificationService.class);
    emailSender = mock(EmailSender.class);
    ticketService = new TicketService(ticketRepository, notificationService, emailSender, FRONTEND);
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

  private Ticket guestTicket() {
    Ticket ticket = Ticket.forGuest(EMAIL, IP, "old-hash", TicketCategory.TECHNICAL, "wifi", "no wifi in B204");
    ReflectionTestUtils.setField(ticket, "id", TICKET);
    return ticket;
  }

  private Ticket studentTicket(Student author) {
    Ticket ticket = new Ticket(author, TicketCategory.TECHNICAL, "wifi", "no wifi in B204");
    ReflectionTestUtils.setField(ticket, "id", TICKET);
    return ticket;
  }

  @Test
  @DisplayName("The Staff queue asks only for the 3 non-Account categories, a negative page is the first, and "
      + "comes with the Open count")
  void staffQueue() {
    // Arrange
    List<TicketCategory> staff = List.of(TicketCategory.GRADES_AND_ENROLMENT, TicketCategory.TECHNICAL,
        TicketCategory.OTHER);
    when(ticketRepository.findQueue(eq(staff), eq(PageRequest.of(0, 20))))
        .thenReturn(new PageImpl<>(List.of(guestTicket())));
    when(ticketRepository.countByCategoryInAndStatus(staff, TicketStatus.OPEN)).thenReturn(4L);

    // Act
    QueueDTO queue = ticketService.listQueue(Role.STAFF, -1);

    // Assert
    assertEquals(4, queue.openCount());
    assertEquals(1, queue.tickets().content().size());
    assertEquals(EMAIL, queue.tickets().content().get(0).guestEmail());
    assertNull(queue.tickets().content().get(0).authorName());
  }

  @Test
  @DisplayName("Resolving a student's ticket notifies them with the subject, and sends no email")
  void resolveNotifies() {
    // Arrange
    Student author = ModelTestData.student("Ana");
    Ticket ticket = studentTicket(author);
    when(ticketRepository.findLockedByIdAndCategoryIn(TICKET, TicketCategory.inQueue(Role.STAFF)))
        .thenReturn(Optional.of(ticket));

    // Act
    QueueTicketDTO result = ticketService.resolve(Role.STAFF, TICKET);

    // Assert
    assertEquals(TicketStatus.RESOLVED, result.status());
    verify(notificationService).notify(author, NotificationType.TICKET_RESOLVED, NotificationTarget.TICKET, TICKET,
        Map.of("subject", "wifi"));
    verify(emailSender, never()).sendSupportTicketUpdate(anyString(), anyString(), anyString(), anyBoolean());
  }

  @Test
  @DisplayName("Resolving a guest's ticket emails a new link and keeps only its hash, the old one stops working")
  void resolveEmailsGuest() throws Exception {
    // Arrange
    Ticket ticket = guestTicket();
    when(ticketRepository.findLockedByIdAndCategoryIn(TICKET, TicketCategory.inQueue(Role.STAFF)))
        .thenReturn(Optional.of(ticket));
    ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);

    // Act
    ticketService.resolve(Role.STAFF, TICKET);

    // Assert
    verify(emailSender).sendSupportTicketUpdate(eq(EMAIL), eq("wifi"), link.capture(), eq(true));
    String token = link.getValue().substring((FRONTEND + "/support/").length());
    assertEquals(43, token.length());
    assertEquals(sha256(token), ticket.getGuestTokenHash());
    verify(notificationService, never()).notify(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Answering a guest's ticket emails a new link that says it was answered, not resolved")
  void answerEmailsGuest() {
    // Arrange
    Ticket ticket = guestTicket();

    // Act
    ticketService.tellAuthor(ticket, NotificationType.TICKET_ANSWERED);

    // Assert
    verify(emailSender).sendSupportTicketUpdate(eq(EMAIL), eq("wifi"), startsWith(FRONTEND + "/support/"),
        eq(false));
    assertNotEquals("old-hash", ticket.getGuestTokenHash());
  }

  @Test
  @DisplayName("Resolving a Resolved ticket is TICKET_ALREADY_RESOLVED and tells nobody")
  void resolveTwice() {
    // Arrange
    Ticket ticket = studentTicket(ModelTestData.student("Ana"));
    ReflectionTestUtils.setField(ticket, "status", TicketStatus.RESOLVED);
    when(ticketRepository.findLockedByIdAndCategoryIn(TICKET, TicketCategory.inQueue(Role.STAFF)))
        .thenReturn(Optional.of(ticket));

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class,
        () -> ticketService.resolve(Role.STAFF, TICKET));

    // Assert
    assertEquals(ErrorCode.TICKET_ALREADY_RESOLVED, error.getCode());
    verify(notificationService, never()).notify(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("An Account ticket isn't in the Staff queue, so Staff get NOT_FOUND")
  void otherQueue() {
    // Arrange
    when(ticketRepository.findByIdAndCategoryIn(TICKET, TicketCategory.inQueue(Role.STAFF)))
        .thenReturn(Optional.empty());

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class,
        () -> ticketService.getInQueue(Role.STAFF, TICKET));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, error.getCode());
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
