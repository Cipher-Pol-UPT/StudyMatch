package com.studymatch.services;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.ReactionDTO;
import com.studymatch.enums.ChatEventType;
import com.studymatch.events.MemberChatEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Message;
import com.studymatch.models.Reaction;
import com.studymatch.models.User;
import com.studymatch.repositories.ReactionRepository;

@Service
public class ReactionService {

  // one that shows as an emoji by itself, or a symbol made one by FE0F. only one way to write each,
  // or the same emoji would be counted as two. a skin tone goes after the ones that take it
  private static final String SINGLE = "(?:\\p{IsEmoji_Modifier_Base}\\p{IsEmoji_Modifier}"
      + "|[\\p{IsEmoji_Presentation}&&[^\\x{1F1E6}-\\x{1F1FF}]]"
      + "|[\\p{IsEmoji}&&[^\\p{IsEmoji_Presentation}0-9#*]]\\x{FE0F})";

  // a flag, a keycap, a region's flag (Scotland) or singles joined by 200D (a family)
  private static final Pattern EMOJI = Pattern.compile("[\\x{1F1E6}-\\x{1F1FF}]{2}"
      + "|[0-9#*]\\x{FE0F}\\x{20E3}"
      + "|\\x{1F3F4}[\\x{E0020}-\\x{E007E}]+\\x{E007F}"
      + "|" + SINGLE + "(?:\\x{200D}" + SINGLE + ")*");

  private final ReactionRepository reactionRepository;
  private final ApplicationEventPublisher eventPublisher;

  public ReactionService(ReactionRepository reactionRepository, ApplicationEventPublisher eventPublisher) {
    this.reactionRepository = reactionRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional(readOnly = true)
  public List<ReactionDTO> list(List<Message> messages, User viewer) {
    if (messages.isEmpty()) {
      return List.of();
    }
    return reactionRepository.findVisible(messages, viewer.getNumber()).stream().map(ReactionDTO::from).toList();
  }

  // one each, so it replaces the one they had. the same emoji again changes nothing, not even who it's hidden from.
  // hidden from a blocker, they only see the one before go, if they could see it (GR9)
  @Transactional
  public ReactionDTO react(Message message, User user, String emoji, User hiddenFrom) {
    if (!isEmoji(emoji)) {
      throw new StudyMatchException(ErrorCode.INVALID_EMOJI);
    }
    ReactionDTO reacted = ReactionDTO.of(message, user, emoji);
    Optional<Reaction> previous = reactionRepository.findLocked(message, user.getNumber());
    boolean blockerSawPrevious = previous.isPresent() && hiddenFrom != null
        && !previous.get().isHiddenFrom(hiddenFrom);
    if (previous.isEmpty()) {
      Long hiddenFromNumber = hiddenFrom == null ? null : hiddenFrom.getNumber();
      if (reactionRepository.upsert(message.getId(), user.getNumber(), emoji, hiddenFromNumber) == 0) {
        return reacted;
      }
    } else if (previous.get().getEmoji().equals(emoji)) {
      return reacted;
    } else {
      previous.get().replace(emoji, hiddenFrom);
    }
    for (User member : message.getChat().getMembers()) {
      if (hiddenFrom == null || !hiddenFrom.getNumber().equals(member.getNumber())) {
        publish(member, reacted);
      } else if (blockerSawPrevious) {
        publish(member, ReactionDTO.of(message, user, null));
      }
    }
    return reacted;
  }

  // taking off one that isn't there changes nothing
  @Transactional
  public void remove(Message message, User user) {
    reactionRepository.findLocked(message, user.getNumber()).ifPresent(reaction -> {
      reactionRepository.delete(reaction);
      for (User member : message.getChat().getMembers()) {
        if (!reaction.isHiddenFrom(member)) {
          publish(member, ReactionDTO.of(message, user, null));
        }
      }
    });
  }

  // to each member and never the topic, who sees it isn't always everyone (GR8, GR9)
  private void publish(User member, ReactionDTO reaction) {
    eventPublisher.publishEvent(
        new MemberChatEvent(member, reaction.chatId(), ChatEventType.MESSAGE_REACTED, reaction));
  }

  private static boolean isEmoji(String text) {
    return text.length() <= Reaction.MAX_EMOJI_LENGTH && EMOJI.matcher(text).matches();
  }

}
