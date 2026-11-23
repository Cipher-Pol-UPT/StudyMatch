package com.studymatch.dtos;

import com.studymatch.models.CustomChat;

// what an edit can change in a chat list row, picture like the row's (#234)
public record ChatEditedDTO(Long chatId, String name, boolean picture) {

  public static ChatEditedDTO from(CustomChat chat) {
    return new ChatEditedDTO(chat.getId(), chat.getName(), chat.getPicture() != null);
  }

}
