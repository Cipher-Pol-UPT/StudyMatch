package com.studymatch.utils;

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

  // the only place the link exists, the ticket keeps just its hash (#366). in the background like the reset code,
  // the ticket is saved either way
  @Async
  public void sendSupportTicketLink(String to, String ticketSubject, String link) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject("StudyMatch - O seu pedido de apoio / Your support request");
    message.setText("""
        Olá,

        Recebemos o seu pedido "%1$s". Pode acompanhá-lo e responder aqui:
        %2$s
        Não partilhe esta ligação, quem a tiver pode ver o pedido.

        ---

        Hello,

        We got your request "%1$s". You can follow it and reply here:
        %2$s
        Don't share this link, anyone who has it can see the request.
        """.formatted(ticketSubject, link));

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.error("Could not send the support ticket link", e);
    }
  }

  // a guest has no notifications, so the queue answering or resolving comes by email. the old link can't be sent
  // again, so it's a new one (#364)
  @Async
  public void sendSupportTicketUpdate(String to, String ticketSubject, String link, boolean resolved) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject("StudyMatch - O seu pedido de apoio / Your support request");
    message.setText("""
        Olá,

        %1$s "%2$s". Pode vê-lo e responder aqui:
        %3$s
        As ligações anteriores deixaram de funcionar. Não partilhe esta, quem a tiver pode ver o pedido.

        ---

        Hello,

        %4$s "%2$s". You can see it and reply here:
        %3$s
        Earlier links don't work anymore. Don't share this one, anyone who has it can see the request.
        """.formatted(resolved ? "Foi resolvido o seu pedido" : "Responderam ao seu pedido", ticketSubject, link,
        resolved ? "We resolved your request" : "There's a new answer on your request"));

    try {
      mailSender.send(message);
    } catch (MailException e) {
      log.error("Could not send the support ticket update", e);
    }
  }

  // not async and not silent: the admin is waiting and has to know if it failed
  public void sendAccountCredentials(String to, String name, String institutionalEmail, String oneTimePassword) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject("StudyMatch - A sua conta / Your account");
    message.setText("""
        Olá %1$s,

        Foi criada uma conta StudyMatch para si.
        Email institucional: %2$s
        Palavra-passe temporária: %3$s
        No primeiro início de sessão vai ter de escolher uma nova palavra-passe.

        ---

        Hello %1$s,

        A StudyMatch account was created for you.
        Institutional email: %2$s
        Temporary password: %3$s
        You'll be asked to choose a new password the first time you log in.
        """.formatted(name, institutionalEmail, oneTimePassword));

    mailSender.send(message);
  }

}
