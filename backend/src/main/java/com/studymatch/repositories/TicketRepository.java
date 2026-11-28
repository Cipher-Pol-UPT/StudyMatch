package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

  // a guest only reaches their ticket through the link (#366)
  Optional<Ticket> findByGuestTokenHash(String guestTokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Ticket> findLockedByGuestTokenHash(String guestTokenHash);

  long countByGuestEmailIgnoreCaseAndCreatedAtAfter(String guestEmail, LocalDateTime since);

  long countByGuestIpAndCreatedAtAfter(String guestIp, LocalDateTime since);

  // no row stands for an email or an ip, so two guest tickets from one wait on a lock named after it, otherwise
  // both would pass the count. the void function in from gives one row, so there's something to return
  @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
  int lock(String key);

}
