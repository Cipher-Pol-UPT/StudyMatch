package com.studymatch.repositories;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.User;
import com.studymatch.models.UserBlock;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

  boolean existsByBlocker_NumberAndBlocked_Number(Long blockerNumber, Long blockedNumber);

  // the blocked users page, by name
  @Query("select b.blocked from UserBlock b where b.blocker.number = :number order by b.blocked.name, b.blocked.number")
  List<User> findBlockedBy(Long number);

  // just the numbers, to filter the chat list
  @Query("select b.blocked.number from UserBlock b where b.blocker.number = :number")
  Set<Long> findBlockedNumbers(Long number);

  // double clicks and two tabs both insert, so the pair is skipped if it's already there
  @Modifying
  @Query(nativeQuery = true, value = """
      insert into user_block (blocker_number, blocked_number, created_at)
      values (:blockerNumber, :blockedNumber, now())
      on conflict (blocker_number, blocked_number) do nothing""")
  void insertIfMissing(Long blockerNumber, Long blockedNumber);

  @Modifying
  @Query("delete from UserBlock b where b.blocker.number = :blockerNumber and b.blocked.number = :blockedNumber")
  void deleteByPair(Long blockerNumber, Long blockedNumber);

}
