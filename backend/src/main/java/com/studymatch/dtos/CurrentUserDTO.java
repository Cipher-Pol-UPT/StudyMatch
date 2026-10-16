package com.studymatch.dtos;

import com.studymatch.enums.Role;
import com.studymatch.models.User;

public record CurrentUserDTO(Long number, String name, String institutionalEmail, Role role,
    boolean mustChangePassword, boolean personalityTestRequired) {

  public static CurrentUserDTO from(User user) {
    return new CurrentUserDTO(user.getNumber(), user.getName(), user.getInstitutionalEmail(), user.getRole(),
        user.isMustChangePassword(), user.requiresPersonalityTest());
  }

}
