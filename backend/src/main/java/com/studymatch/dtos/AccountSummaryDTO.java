package com.studymatch.dtos;

import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.Role;
import com.studymatch.models.User;

// one row of the admin's account list
public record AccountSummaryDTO(Long number, String name, String institutionalEmail, Role role,
    AccountStatus accountStatus) {

  public static AccountSummaryDTO from(User user) {
    return new AccountSummaryDTO(user.getNumber(), user.getName(), user.getInstitutionalEmail(), user.getRole(),
        user.getAccountStatus());
  }

}
