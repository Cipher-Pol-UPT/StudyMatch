package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// memberNumbers is everyone but the creator, picture is an optional data URL like the profile photo
public record CreateGroupChatRequestDTO(@NotBlank @Size(max = 50) String name, String picture,
    @NotNull List<@NotNull Long> memberNumbers) {

  public CreateGroupChatRequestDTO {
    if (name != null) {
      name = name.strip();
    }
  }

}
