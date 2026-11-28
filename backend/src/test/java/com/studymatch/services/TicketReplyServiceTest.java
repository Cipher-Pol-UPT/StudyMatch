package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.ReplyTicketRequestDTO;
import com.studymatch.dtos.TicketDTO;
import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.ModelTestData;
import com.studymatch.models.Student;
import com.studymatch.models.Ticket;
import com.studymatch.models.TicketReply;
import com.studymatch.repositories.TicketReplyRepository;

class TicketReplyServiceTest {

  private static final Long AUTHOR = 1001L;
  private static final Long TICKET = 7L;

  private TicketReplyRepository ticketReplyRepository;
  private TicketService ticketService;
  private TicketReplyService ticketReplyService;
  private Student author;
  private Ticket ticket;

  @BeforeEach
  void setUp() {
    ticketReplyRepository = mock(TicketReplyRepository.class);
    ticketService = mock(TicketService.class);
    ticketReplyService = new TicketReplyService(ticketReplyRepository, ticketService);
    author = ModelTestData.student("Ana");
    ReflectionTestUtils.setField(author, "number", AUTHOR);
    ticket = new Ticket(author, TicketCategory.TECHNICAL, "wifi", "no wifi in B204");
    ReflectionTestUtils.setField(ticket, "id", TICKET);
  }

  @Test
  @DisplayName("Replying to a Resolved ticket saves the stripped text and opens the ticket again")
  void replyReopens() {
    // Arrange
    ReflectionTestUtils.setField(ticket, "status", TicketStatus.RESOLVED);
    when(ticketService.getMineLocked(AUTHOR, TICKET)).thenReturn(ticket);
    ArgumentCaptor<TicketReply> saved = ArgumentCaptor.forClass(TicketReply.class);

    // Act
    TicketDTO result = ticketReplyService.replyToMine(author, TICKET, new ReplyTicketRequestDTO("  still broken  "));

    // Assert
    assertEquals(TicketStatus.OPEN, result.status());
    verify(ticketReplyRepository).save(saved.capture());
    assertEquals("still broken", saved.getValue().getText());
    assertEquals(author, saved.getValue().getAuthor());
  }

  @Test
  @DisplayName("Replying to someone else's ticket is NOT_FOUND and saves nothing")
  void replyNotMine() {
    // Arrange
    when(ticketService.getMineLocked(AUTHOR, TICKET)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException error = assertThrows(StudyMatchException.class,
        () -> ticketReplyService.replyToMine(author, TICKET, new ReplyTicketRequestDTO("hello")));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, error.getCode());
    verify(ticketReplyRepository, never()).save(any(TicketReply.class));
  }

  @Test
  @DisplayName("Viewing a ticket gives it with its thread in the repository's order")
  void view() {
    // Arrange
    when(ticketService.getMine(AUTHOR, TICKET)).thenReturn(ticket);
    when(ticketReplyRepository.findByTicket_IdOrderByIdAsc(TICKET))
        .thenReturn(List.of(new TicketReply(ticket, author, "first"), new TicketReply(ticket, author, "second")));

    // Act
    TicketDTO result = ticketReplyService.viewMine(AUTHOR, TICKET);

    // Assert
    assertEquals("no wifi in B204", result.description());
    assertEquals(List.of("first", "second"), result.replies().stream().map(reply -> reply.text()).toList());
  }

}
