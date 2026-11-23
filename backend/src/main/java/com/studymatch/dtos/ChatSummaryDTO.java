package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.models.Chat;
import com.studymatch.models.CustomChat;
import com.studymatch.models.DirectChat;
import com.studymatch.models.User;

// a row of the chat list, contact is the other person in a DM and null in a group chat.
// picture says if a group chat has one at /chats/{id}/picture, a DM uses the contact's photo.
// pictureUpdatedAt goes in its url, so a new picture isn't served from the cache.
// lastMessage is null in a group chat nobody has written in yet
public record ChatSummaryDTO(Long chatId, String name, ChatContactDTO contact, boolean picture,
    LocalDateTime pictureUpdatedAt, MessageDTO lastMessage, long unread) {

  public static ChatSummaryDTO from(Chat chat, User viewer, MessageDTO lastMessage, long unread) {
    ChatContactDTO contact = chat instanceof DirectChat direct ? ChatContactDTO.from(direct.getOtherUser(viewer)) : null;
    boolean picture = chat instanceof CustomChat custom && custom.getPicture() != null;
    LocalDateTime pictureUpdatedAt = chat instanceof CustomChat custom ? custom.getPictureUpdatedAt() : null;
    return new ChatSummaryDTO(chat.getId(), chat.getNameFor(viewer), contact, picture, pictureUpdatedAt, lastMessage,
        unread);
  }

}
