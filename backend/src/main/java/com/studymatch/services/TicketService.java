package com.studymatch.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.GuestTicketRequestDTO;
import com.studymatch.dtos.OpenTicketRequestDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.dtos.QueueDTO;
import com.studymatch.dtos.QueueTicketDTO;
import com.studymatch.dtos.TicketSummaryDTO;
import com.studymatch.enums.NotificationTarget;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.Role;
import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Ticket;
import com.studymatch.models.User;
import com.studymatch.repositories.TicketRepository;
import com.studymatch.utils.EmailSender;

// the helpdesk. the author's side, logged in or a guest with the link, only finds their own tickets (#362, #366).
// the queue's side only finds its own queue (#364, GR15)
@Service
public class TicketService {

  private static final Duration GUEST_WINDOW = Duration.ofHours(1);
  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private static final int TICKETS_PER_PAGE = 20;

  private final TicketRepository ticketRepository;
  private final NotificationService notificationService;
  private final EmailSender emailSender;
  private final String frontendUrl;

  public TicketService(TicketRepository ticketRepository, NotificationService notificationService,
      EmailSender emailSender, @Value("${studymatch.frontend-url}") String frontendUrl) {
    this.ticketRepository = ticketRepository;
    this.notificationService = notificationService;
    this.emailSender = emailSender;
    this.frontendUrl = frontendUrl;
  }

  // it starts Open, and its category already says which queue sees it
  @Transactional
  public TicketSummaryDTO open(User author, OpenTicketRequestDTO request) {
    Ticket ticket = new Ticket(author, request.category(), request.subject(), request.description());
    return TicketSummaryDTO.from(ticketRepository.save(ticket));
  }

  @Transactional(readOnly = true)
  public List<TicketSummaryDTO> listMine(Long authorNumber) {
    return ticketRepository.findByAuthor_NumberOrderByUpdatedAtDescIdDesc(authorNumber).stream()
        .map(TicketSummaryDTO::from).toList();
  }

  public Ticket getMine(Long authorNumber, Long ticketId) {
    return ticketRepository.findByIdAndAuthor_Number(ticketId, authorNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  public Ticket getMineLocked(Long authorNumber, Long ticketId) {
    return ticketRepository.findLockedByIdAndAuthor_Number(ticketId, authorNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // public, so a few per hour per email and per ip. the token only goes in the email, so having the link means
  // having the inbox (#366)
  @Transactional
  public void openAsGuest(GuestTicketRequestDTO request, String ip) {
    String email = request.email().strip();
    ticketRepository.lock("ticket-email:" + email.toLowerCase(Locale.ROOT));
    ticketRepository.lock("ticket-ip:" + ip);
    LocalDateTime since = LocalDateTime.now().minus(GUEST_WINDOW);
    if (ticketRepository.countByGuestEmailIgnoreCaseAndCreatedAtAfter(email, since)
        >= Ticket.MAX_GUEST_TICKETS_PER_EMAIL) {
      throw new StudyMatchException(ErrorCode.TOO_MANY_TICKETS, Map.of("max", Ticket.MAX_GUEST_TICKETS_PER_EMAIL));
    }
    if (ticketRepository.countByGuestIpAndCreatedAtAfter(ip, since) >= Ticket.MAX_GUEST_TICKETS_PER_IP) {
      throw new StudyMatchException(ErrorCode.TOO_MANY_TICKETS, Map.of("max", Ticket.MAX_GUEST_TICKETS_PER_IP));
    }

    String token = newToken();
    Ticket ticket = ticketRepository.save(Ticket.forGuest(email, ip, hash(token), request.category(),
        request.subject(), request.description()));
    emailSender.sendSupportTicketLink(email, ticket.getSubject(), frontendUrl + "/support/" + token);
  }

  // a wrong link looks the same as a ticket that doesn't exist
  public Ticket getGuest(String token) {
    return ticketRepository.findByGuestTokenHash(hash(token))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  public Ticket getGuestLocked(String token) {
    return ticketRepository.findLockedByGuestTokenHash(hash(token))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // the queue's side (#364). the role picks the queue, so staff never see the admin's and the other way round
  @Transactional(readOnly = true)
  public QueueDTO listQueue(Role role, int page) {
    List<TicketCategory> categories = TicketCategory.inQueue(role);
    PageRequest pageRequest = PageRequest.of(Math.max(page, 0), TICKETS_PER_PAGE);
    return new QueueDTO(ticketRepository.countByCategoryInAndStatus(categories, TicketStatus.OPEN),
        PageDTO.from(ticketRepository.findQueue(categories, pageRequest), QueueTicketDTO::from));
  }

  // one from the other queue is NOT_FOUND, as if it didn't exist
  public Ticket getInQueue(Role role, Long ticketId) {
    return ticketRepository.findByIdAndCategoryIn(ticketId, TicketCategory.inQueue(role))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  public Ticket getInQueueLocked(Role role, Long ticketId) {
    return ticketRepository.findLockedByIdAndCategoryIn(ticketId, TicketCategory.inQueue(role))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  @Transactional
  public QueueTicketDTO resolve(Role role, Long ticketId) {
    Ticket ticket = getInQueueLocked(role, ticketId);
    ticket.resolve();
    tellAuthor(ticket, NotificationType.TICKET_RESOLVED);
    return QueueTicketDTO.from(ticket);
  }

  // a guest has no notifications, so they get the news by email with a new link, the old one stops working
  public void tellAuthor(Ticket ticket, NotificationType type) {
    if (ticket.isFromGuest()) {
      String token = newToken();
      ticket.replaceGuestToken(hash(token));
      emailSender.sendSupportTicketUpdate(ticket.getGuestEmail(), ticket.getSubject(),
          frontendUrl + "/support/" + token, type == NotificationType.TICKET_RESOLVED);
      return;
    }
    notificationService.notify(ticket.getAuthor(), type, NotificationTarget.TICKET, ticket.getId(),
        Map.of("subject", ticket.getSubject()));
  }

  // 256 random bits, so it can't be guessed and a plain hash is enough to look it up
  private static String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("every java has sha-256", e);
    }
  }

}
