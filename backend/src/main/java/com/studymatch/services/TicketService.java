package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.OpenTicketRequestDTO;
import com.studymatch.dtos.TicketSummaryDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Ticket;
import com.studymatch.models.User;
import com.studymatch.repositories.TicketRepository;

// the author's side of the helpdesk. every call only finds their own tickets (#362, GR15)
@Service
public class TicketService {

  private final TicketRepository ticketRepository;

  public TicketService(TicketRepository ticketRepository) {
    this.ticketRepository = ticketRepository;
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

}
