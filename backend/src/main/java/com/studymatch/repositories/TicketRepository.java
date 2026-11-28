package com.studymatch.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.enums.TicketCategory;
import com.studymatch.enums.TicketStatus;
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

  // a queue, open first. the status is stored as text, sorting on it would put In progress before Open (#364)
  @Query(value = """
      select t from Ticket t left join fetch t.author where t.category in :categories
      order by case t.status when com.studymatch.enums.TicketStatus.OPEN then 0
        when com.studymatch.enums.TicketStatus.IN_PROGRESS then 1 else 2 end, t.updatedAt desc, t.id desc""",
      countQuery = "select count(t) from Ticket t where t.category in :categories")
  Page<Ticket> findQueue(Collection<TicketCategory> categories, Pageable pageable);

  long countByCategoryInAndStatus(Collection<TicketCategory> categories, TicketStatus status);

  // a ticket from the other queue looks the same as one that doesn't exist
  Optional<Ticket> findByIdAndCategoryIn(Long id, Collection<TicketCategory> categories);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Ticket> findLockedByIdAndCategoryIn(Long id, Collection<TicketCategory> categories);

  long countByGuestEmailIgnoreCaseAndCreatedAtAfter(String guestEmail, LocalDateTime since);

  long countByGuestIpAndCreatedAtAfter(String guestIp, LocalDateTime since);

  // no row stands for an email or an ip, so two guest tickets from one wait on a lock named after it, otherwise
  // both would pass the count. the void function in from gives one row, so there's something to return
  @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
  int lock(String key);

}
