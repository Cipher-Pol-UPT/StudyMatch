package com.studymatch.dtos;

import com.studymatch.enums.Role;
import com.studymatch.models.User;

// what the admin sees after creating an account, the one-time password only goes by email
public record CreatedAccountDTO(Long number, String name, String institutionalEmail, Role type) {

  public static CreatedAccountDTO from(User user) {
    return new CreatedAccountDTO(user.getNumber(), user.getName(), user.getInstitutionalEmail(), user.getRole());
  }

}
