package com.studymatch.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.studymatch.models.User;

@Component
public class EmailSender {

  private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

  private final JavaMailSender mailSender;
  private final String from;

  public EmailSender(JavaMailSender mailSender, @Value("${studymatch.mail-from}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  // runs in the background so the response doesn't take longer when the account exists
  @Async
  public void sendPasswordResetCode(String to, String code) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject("StudyMatch - Código de recuperação / Recovery code");
    message.setText("""
        Olá,

        O seu código para redefinir a palavra-passe do StudyMatch é: %1$s
        O código é válido durante %2$d minutos. Se não pediu esta alteração, ignore este email.

        ---

        Hello,

        Your code to reset your StudyMatch password is: %1$s
        The code is valid for %2$d minutes. If you didn't ask for this, you can ignore this email.
        """.formatted(code, User.RESET_CODE_VALID_MINUTES));

    // silent on purpose, telling the user it failed would give away the account exists
    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.error("Could not send the password reset email", e);
    }
  }

}
