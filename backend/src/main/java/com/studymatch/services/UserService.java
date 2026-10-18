package com.studymatch.services;

import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.studymatch.dtos.ChangePasswordRequestDTO;
import com.studymatch.dtos.ResetPasswordRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.mail.EmailSender;
import com.studymatch.models.User;
import com.studymatch.repositories.UserRepository;
import com.studymatch.security.PasswordPolicy;

@Service
public class UserService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailSender emailSender;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailSender emailSender) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailSender = emailSender;
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
    if (!PasswordPolicy.fitsInBcrypt(newPassword)) {
      throw new StudyMatchException(ErrorCode.PASSWORD_TOO_LONG);
    }
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new StudyMatchException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    user.setMustChangePassword(false);
    return userRepository.save(user);
  }

  public void requestPasswordReset(String institutionalEmail) {
    // hashed before knowing if the account exists, so both cases take the same time
    String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
    String codeHash = passwordEncoder.encode(code);

    Optional<User> found = findByInstitutionalEmail(institutionalEmail);
    if (found.isEmpty() || found.get().isBlocked()) {
      return;
    }

    User user = found.get();
    user.startPasswordReset(codeHash);
    userRepository.save(user);
    // personal email on purpose: it's the recovery contact, the institutional one might need the account to read it
    emailSender.sendPasswordResetCode(user.getPersonalEmail(), code);
  }

  public void resetPassword(ResetPasswordRequestDTO request) {
    // unknown email, no code or an expired one all get the same answer
    User user = findByInstitutionalEmail(request.email())
        .filter(User::hasActiveResetCode)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.RESET_CODE_INVALID));

    if (!passwordEncoder.matches(request.code(), user.getResetCodeHash())) {
      user.registerFailedResetAttempt();
      userRepository.save(user);
      throw new StudyMatchException(ErrorCode.RESET_CODE_INVALID);
    }
    if (!PasswordPolicy.fitsInBcrypt(request.newPassword())) {
      throw new StudyMatchException(ErrorCode.PASSWORD_TOO_LONG);
    }

    user.setPassword(passwordEncoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    user.clearPasswordReset();
    userRepository.save(user);
  }

}
