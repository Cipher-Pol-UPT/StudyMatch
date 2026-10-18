package com.studymatch.services;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.studymatch.dtos.ChangePasswordRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.User;
import com.studymatch.repositories.UserRepository;

@Service
public class UserService {

  // BCrypt only looks at the first 72 bytes and refuses to hash anything longer
  private static final int MAX_PASSWORD_BYTES = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public Optional<User> findByInstitutionalEmail(String institutionalEmail) {
    return userRepository.findByInstitutionalEmail(institutionalEmail.trim().toLowerCase());
  }

  public User getByInstitutionalEmail(String institutionalEmail) {
    return findByInstitutionalEmail(institutionalEmail)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  public User changePassword(String institutionalEmail, ChangePasswordRequestDTO request) {
    User user = getByInstitutionalEmail(institutionalEmail);

    // on the forced first change the one-time password was just checked at login, so it's not asked again
    if (!user.isMustChangePassword()) {
      String currentPassword = request.currentPassword();
      if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
        throw new StudyMatchException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
      }
    }

    String newPassword = request.newPassword();
    if (newPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new StudyMatchException(ErrorCode.PASSWORD_TOO_LONG);
    }
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new StudyMatchException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    user.setMustChangePassword(false);
    return userRepository.save(user);
  }

}
