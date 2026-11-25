package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.ReactionRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.services.MessageService;

// two tabs reacting at once, and reacting while the message is deleted. real commits, so it cleans up after itself
@SpringBootTest
@ActiveProfiles("test")
class ReactMessageConcurrencyIntegrationTest {

  private static final String HEART = "❤️";
  private static final String LAUGH = "😂";

  // long enough for the other thread to reach the lock and be seen waiting
  private static final long WAITING_MILLIS = 700;

  @Autowired
  private MessageService messageService;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private MessageRepository messageRepository;

  @Autowired
  private ReactionRepository reactionRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
  private TransactionTemplate transaction;
  private Degree degree;
  private Student ana;
  private Student rui;
  private DirectChat chat;
  private Message message;

  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
    degree = degreeRepository.save(new Degree("IT Concurrent React Degree", 3));
    ana = saveStudent("it.concreact.ana@example.com", "Ana Concurrent React");
    rui = saveStudent("it.concreact.rui@example.com", "Rui Concurrent React");
    chat = chatRepository.save(new DirectChat(ana, rui));
    message = messageRepository.save(new Message(chat, rui, "lunch?"));
  }

  @AfterEach
  void cleanUp() {
    otherThread.shutdownNow();
    transaction.executeWithoutResult(status -> messageRepository.deleteByChat(chat));
    chatRepository.deleteById(chat.getId());
    studentRepository.deleteAll(List.of(ana, rui));
    degreeRepository.delete(degree);
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, "hash", degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  private void react(String emoji) {
    messageService.react(chat.getId(), message.getId(), ana, emoji);
  }

  private void removeReaction() {
    messageService.removeReaction(chat.getId(), message.getId(), ana);
  }

  private void deleteMessage() {
    messageService.delete(chat.getId(), message.getId(), rui);
  }

  private List<String> emojis() {
    return reactionRepository.findVisible(List.of(message), ana.getNumber()).stream()
        .map(reaction -> reaction.getEmoji())
        .toList();
  }

  // the first is done but not committed when the second starts, which has to wait for it
  private Future<?> during(Runnable first, Runnable second) {
    Future<?>[] other = new Future<?>[1];
    transaction.executeWithoutResult(status -> {
      first.run();
      reactionRepository.flush();
      other[0] = otherThread.submit(second);
      assertThrows(TimeoutException.class, () -> other[0].get(WAITING_MILLIS, TimeUnit.MILLISECONDS));
    });
    return other[0];
  }

  @Test
  @DisplayName("Two tabs with a first reaction each: one is left, the last one")
  void twoFirstReactions() throws Exception {
    // Act
    during(() -> react(HEART), () -> react(LAUGH)).get(5, TimeUnit.SECONDS);

    // Assert
    assertEquals(List.of(LAUGH), emojis());
  }

  @Test
  @DisplayName("A double click sends the same one twice and it's there once")
  void sameFirstReaction() throws Exception {
    // Act
    during(() -> react(HEART), () -> react(HEART)).get(5, TimeUnit.SECONDS);

    // Assert
    assertEquals(List.of(HEART), emojis());
  }

  @Test
  @DisplayName("Two tabs taking it off at once both go through")
  void bothRemove() throws Exception {
    // Arrange
    react(HEART);

    // Act
    during(this::removeReaction, this::removeReaction).get(5, TimeUnit.SECONDS);

    // Assert
    assertEquals(List.of(), emojis());
  }

  @Test
  @DisplayName("Replacing it while another tab takes it off adds the new one")
  void replaceDuringRemove() throws Exception {
    // Arrange
    react(HEART);

    // Act
    during(this::removeReaction, () -> react(LAUGH)).get(5, TimeUnit.SECONDS);

    // Assert
    assertEquals(List.of(LAUGH), emojis());
  }

  @Test
  @DisplayName("Deleting the message during a reaction waits for it, then both are gone")
  void deleteWaitsForTheReaction() throws Exception {
    // Act
    during(() -> react(HEART), this::deleteMessage).get(5, TimeUnit.SECONDS);

    // Assert
    assertFalse(messageRepository.existsById(message.getId()));
    assertEquals(List.of(), emojis());
  }

  @Test
  @DisplayName("Reacting while the message is being deleted waits, then it's NOT_FOUND and not a 500")
  void reactionWaitsForTheDelete() {
    // Act
    Future<?> reacted = during(this::deleteMessage, () -> react(HEART));

    // Assert
    ExecutionException e = assertThrows(ExecutionException.class, () -> reacted.get(5, TimeUnit.SECONDS));
    StudyMatchException refused = assertInstanceOf(StudyMatchException.class, e.getCause());
    assertEquals(ErrorCode.NOT_FOUND, refused.getCode());
  }

}
