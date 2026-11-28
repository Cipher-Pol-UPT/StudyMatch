package com.studymatch.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ReplyTicketRequestDTO;
import com.studymatch.dtos.TicketDTO;
import com.studymatch.models.Ticket;
import com.studymatch.models.TicketReply;
import com.studymatch.models.User;
import com.studymatch.repositories.TicketReplyRepository;

// a ticket's thread. the author, or the guest with the link, reads it and writes back. the queue's side comes
// with #364
@Service
public class TicketReplyService {

  private final TicketReplyRepository ticketReplyRepository;
  private final TicketService ticketService;

  public TicketReplyService(TicketReplyRepository ticketReplyRepository, TicketService ticketService) {
    this.ticketReplyRepository = ticketReplyRepository;
    this.ticketService = ticketService;
  }

  @Transactional(readOnly = true)
  public TicketDTO viewMine(Long authorNumber, Long ticketId) {
    return withThread(ticketService.getMine(authorNumber, ticketId));
  }

  // writing back on a resolved ticket opens it again (GR15)
  @Transactional
  public TicketDTO replyToMine(User author, Long ticketId, ReplyTicketRequestDTO request) {
    Ticket ticket = ticketService.getMineLocked(author.getNumber(), ticketId);
    ticketReplyRepository.save(new TicketReply(ticket, author, request.text()));
    ticket.authorReplied();
    return withThread(ticket);
  }

  @Transactional(readOnly = true)
  public TicketDTO viewAsGuest(String token) {
    return withThread(ticketService.getGuest(token));
  }

  // the same as the author's reply, written by nobody since a guest has no account (#366)
  @Transactional
  public TicketDTO replyAsGuest(String token, ReplyTicketRequestDTO request) {
    Ticket ticket = ticketService.getGuestLocked(token);
    ticketReplyRepository.save(new TicketReply(ticket, null, request.text()));
    ticket.authorReplied();
    return withThread(ticket);
  }

  private TicketDTO withThread(Ticket ticket) {
    return TicketDTO.from(ticket, ticketReplyRepository.findByTicket_IdOrderByIdAsc(ticket.getId()));
  }

}
