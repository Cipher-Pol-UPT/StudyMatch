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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.GuestTicketRequestDTO;
import com.studymatch.dtos.OpenTicketRequestDTO;
import com.studymatch.dtos.TicketSummaryDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Ticket;
import com.studymatch.models.User;
import com.studymatch.repositories.TicketRepository;
import com.studymatch.utils.EmailSender;

// the author's side of the helpdesk, logged in or a guest with the link. every call only finds their own tickets
// (#362, #366, GR15)
@Service
public class TicketService {

  private static final Duration GUEST_WINDOW = Duration.ofHours(1);
  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final TicketRepository ticketRepository;
  private final EmailSender emailSender;
  private final String frontendUrl;

  public TicketService(TicketRepository ticketRepository, EmailSender emailSender,
      @Value("${studymatch.frontend-url}") String frontendUrl) {
    this.ticketRepository = ticketRepository;
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
