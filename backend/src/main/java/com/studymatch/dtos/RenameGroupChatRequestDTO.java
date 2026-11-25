package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// a group chat's new name, checked like when it's created
public record RenameGroupChatRequestDTO(@NotBlank @Size(max = 50) String name) {

  public RenameGroupChatRequestDTO {
    if (name != null) {
      name = name.strip();
    }
  }

}
