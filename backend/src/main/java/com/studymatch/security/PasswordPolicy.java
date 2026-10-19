package com.studymatch.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

// one place for the password rules, used by every DTO that sets a new password
public final class PasswordPolicy {

  public static final int MIN_LENGTH = 8;
  public static final int MAX_LENGTH = 72;

  // at least one letter (any language) and one digit, in any order
  public static final String PATTERN = "(?=.*\\p{L})(?=.*\\d).*";

  // BCrypt only looks at the first 72 bytes and refuses to hash anything longer
  private static final int MAX_BYTES = 72;

  // no 0/O or 1/l/I, people type this from an email
  private static final String ONE_TIME_PASSWORD_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
  private static final int ONE_TIME_PASSWORD_LENGTH = 12;
  private static final SecureRandom RANDOM = new SecureRandom();

  private PasswordPolicy() {
  }

  public static String generateOneTimePassword() {
    StringBuilder password = new StringBuilder(ONE_TIME_PASSWORD_LENGTH);
    for (int i = 0; i < ONE_TIME_PASSWORD_LENGTH; i++) {
      password.append(ONE_TIME_PASSWORD_CHARACTERS.charAt(RANDOM.nextInt(ONE_TIME_PASSWORD_CHARACTERS.length())));
    }
    return password.toString();
  }

  // @Size counts characters, but é is 2 bytes, so this is checked separately
  public static boolean fitsInBcrypt(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
  }

}
