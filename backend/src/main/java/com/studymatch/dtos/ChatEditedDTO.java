package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.models.CustomChat;
import com.studymatch.models.GroupChat;

// what an edit can change in a chat list row, picture like the row's (#234)
public record ChatEditedDTO(Long chatId, String name, boolean picture, LocalDateTime pictureUpdatedAt) {

  public static ChatEditedDTO from(CustomChat chat) {
    return new ChatEditedDTO(chat.getId(), chat.getName(), chat.getPicture() != null, chat.getPictureUpdatedAt());
  }

  // an assignment chat's name changes when its group is renumbered, it never has a picture (#236)
  public static ChatEditedDTO from(GroupChat chat) {
    return new ChatEditedDTO(chat.getId(), chat.getName(), false, null);
  }

}
