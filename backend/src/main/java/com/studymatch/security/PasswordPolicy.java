package com.studymatch.security;

import java.nio.charset.StandardCharsets;

// one place for the password rules, used by every DTO that sets a new password
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 72;

  // at least one letter (any language) and one digit, in any order
  public static final String PATTERN = "(?=.*\\p{L})(?=.*\\d).*";

  // BCrypt only looks at the first 72 bytes and refuses to hash anything longer
  private static final int MAX_BYTES = 72;

  private PasswordPolicy() {
  }

  // @Size counts characters, but é is 2 bytes, so this is checked separately
  public static boolean fitsInBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
  }

}
