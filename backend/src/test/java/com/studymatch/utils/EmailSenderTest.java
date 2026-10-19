package com.studymatch.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.studymatch.models.User;

class EmailSenderTest {

  private JavaMailSender mailSender;
  private EmailSender emailSender;

  @BeforeEach
  void setUp() {
    // fake mail server, real EmailSender (no Spring here, so @Async does nothing and it runs right away)
    mailSender = mock(JavaMailSender.class);
    emailSender = new EmailSender(mailSender, "no-reply@test.com");
  }

  // the email that was handed to the (fake) mail server
  private SimpleMailMessage sentMessage() {
    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(message.capture());
    return message.getValue();
  }

  @Test
  @DisplayName("The email goes from our address to the given one")
  void addresses() {
    // Act
    emailSender.sendPasswordResetCode("ana@example.com", "123456");

    // Assert
    SimpleMailMessage message = sentMessage();
    assertEquals("no-reply@test.com", message.getFrom());
    assertArrayEquals(new String[] { "ana@example.com" }, message.getTo());
  }

  @Test
  @DisplayName("The text has the code and how long it lasts, in both languages")
  void textHasCodeAndValidity() {
    // Act
    emailSender.sendPasswordResetCode("ana@example.com", "004821");

    // Assert - leading zeros kept, and the minutes come from the same rule the model uses
    String text = sentMessage().getText();
    assertTrue(text.contains("004821"));
    assertTrue(text.contains("durante " + User.RESET_CODE_VALID_MINUTES + " minutos"));
    assertTrue(text.contains("valid for " + User.RESET_CODE_VALID_MINUTES + " minutes"));
  }

  @Test
  @DisplayName("If the mail server fails, nothing blows up")
  void failedSendDoesNotThrow() {
    // Arrange
    doThrow(new MailSendException("mail server down")).when(mailSender).send(any(SimpleMailMessage.class));

    // Act + Assert
    assertDoesNotThrow(() -> emailSender.sendPasswordResetCode("ana@example.com", "123456"));
  }

  // account credentials

  @Test
  @DisplayName("The account email has the name, the institutional email and the one-time password")
  void credentialsEmail() {
    // Act
    emailSender.sendAccountCredentials("ana@example.com", "Ana Test", "ana.test@test.com", "Ab3dEf7hJk9m");

    // Assert
    SimpleMailMessage message = sentMessage();
    assertArrayEquals(new String[] { "ana@example.com" }, message.getTo());
    assertTrue(message.getText().contains("Ana Test"));
    assertTrue(message.getText().contains("ana.test@test.com"));
    assertTrue(message.getText().contains("Ab3dEf7hJk9m"));
  }

  @Test
  @DisplayName("Unlike the reset code, a failed account email is passed on so the admin finds out")
  void failedCredentialsEmailIsPassedOn() {
    // Arrange
    doThrow(new MailSendException("mail server down")).when(mailSender).send(any(SimpleMailMessage.class));

    // Act + Assert
    assertThrows(MailSendException.class,
        () -> emailSender.sendAccountCredentials("ana@example.com", "Ana Test", "ana.test@test.com", "Ab3dEf7hJk9m"));
  }

}
