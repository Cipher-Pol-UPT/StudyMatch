package com.studymatch.services;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ChatContactDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.UserBlockRepository;

@Service
public class UserBlockService {

  private final UserBlockRepository userBlockRepository;
  private final UserService userService;

  public UserBlockService(UserBlockRepository userBlockRepository, UserService userService) {
    this.userBlockRepository = userBlockRepository;
    this.userService = userService;
  }

  // blocking twice changes nothing (GR9)
  @Transactional
  public void block(User user, Long number) {
    userBlockRepository.insertIfMissing(user.getNumber(), getBlockable(user, number).getNumber());
  }

  // unblocking someone who isn't blocked changes nothing either
  @Transactional
  public void unblock(User user, Long number) {
    userBlockRepository.deleteByPair(user.getNumber(), getBlockable(user, number).getNumber());
  }

  @Transactional(readOnly = true)
  public List<ChatContactDTO> listBlocked(User user) {
    return userBlockRepository.findBlockedBy(user.getNumber()).stream().map(ChatContactDTO::from).toList();
  }

  @Transactional(readOnly = true)
  public boolean hasBlocked(User blocker, User blocked) {
    return userBlockRepository.existsByBlocker_NumberAndBlocked_Number(blocker.getNumber(), blocked.getNumber());
  }

  @Transactional(readOnly = true)
  public Set<Long> getBlockedNumbers(User user) {
    return userBlockRepository.findBlockedNumbers(user.getNumber());
  }

  // only people who have chats, the account status doesn't matter
  private User getBlockable(User user, Long number) {
    if (user.getNumber().equals(number)) {
      throw new StudyMatchException(ErrorCode.CANNOT_BLOCK_YOURSELF);
    }
    User other = userService.getByNumber(number);
    if (!(other instanceof Student || other instanceof Teacher)) {
      throw new StudyMatchException(ErrorCode.NOT_FOUND);
    }
    return other;
  }

}
