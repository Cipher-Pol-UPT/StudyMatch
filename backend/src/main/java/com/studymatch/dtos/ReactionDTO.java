package com.studymatch.dtos;

import com.studymatch.models.Message;
import com.studymatch.models.Reaction;
import com.studymatch.models.User;

// one person's reaction to a message, replaces the one they had. a null emoji means they took it off
public record ReactionDTO(Long chatId, Long messageId, ChatContactDTO user, String emoji) {

  public static ReactionDTO from(Reaction reaction) {
    return of(reaction.getMessage(), reaction.getUser(), reaction.getEmoji());
  }

  public static ReactionDTO of(Message message, User user, String emoji) {
    return new ReactionDTO(message.getChat().getId(), message.getId(), ChatContactDTO.from(user), emoji);
  }

}
