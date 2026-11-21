package com.studymatch.dtos;

import com.studymatch.enums.Role;
import com.studymatch.models.User;

// someone you can message, a row of the new message search
public record ChatContactDTO(Long number, String name, Role role) {

  public static ChatContactDTO from(User user) {
    return new ChatContactDTO(user.getNumber(), user.getName(), user.getRole());
  }

}
