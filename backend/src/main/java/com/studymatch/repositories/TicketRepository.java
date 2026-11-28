package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.studymatch.models.Ticket;

import jakarta.persistence.LockModeType;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

  // the last one replied to first
  List<Ticket> findByAuthor_NumberOrderByUpdatedAtDescIdDesc(Long authorNumber);

  // someone else's ticket looks the same as one that doesn't exist
  Optional<Ticket> findByIdAndAuthor_Number(Long id, Long authorNumber);

  // replying changes the status, so a reply and the queue resolving it (#364) go one after the other
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Ticket> findLockedByIdAndAuthor_Number(Long id, Long authorNumber);

}
