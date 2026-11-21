package com.studymatch.dtos;

import com.studymatch.models.Chat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.User;

// a row of the chat list, contact is the other person in a DM and null in a group chat
public record ChatSummaryDTO(Long chatId, String name, ChatContactDTO contact, MessageDTO lastMessage, long unread) {

  public static ChatSummaryDTO from(Chat chat, User viewer, MessageDTO lastMessage, long unread) {
    ChatContactDTO contact = chat instanceof DirectChat direct ? ChatContactDTO.from(direct.getOtherUser(viewer)) : null;
    return new ChatSummaryDTO(chat.getId(), chat.getNameFor(viewer), contact, lastMessage, unread);
  }

}
