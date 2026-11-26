package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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

import com.studymatch.dtos.MessageDTO;
import com.studymatch.dtos.SendMessageRequestDTO;
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
import com.studymatch.repositories.StudentRepository;
import com.studymatch.services.MessageService;

// replying to a message, or editing a reply to it, while it's deleted. real commits, so it cleans up after itself
@SpringBootTest
@ActiveProfiles("test")
class ReplyMessageConcurrencyIntegrationTest {

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
  private PlatformTransactionManager transactionManager;

  private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
  private TransactionTemplate transaction;
  private Degree degree;
  private Student ana;
  private Student rui;
  private DirectChat chat;
  private Message original;

  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
    degree = degreeRepository.save(new Degree("IT Concurrent Reply Degree", 3));
    ana = saveStudent("it.concreply.ana@example.com", "Ana Concurrent Reply");
    rui = saveStudent("it.concreply.rui@example.com", "Rui Concurrent Reply");
    chat = chatRepository.save(new DirectChat(ana, rui));
    original = messageRepository.save(new Message(chat, rui, "lunch?"));
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

  private MessageDTO reply() {
    return messageService.send(chat.getId(), ana, new SendMessageRequestDTO("at one", null, original.getId()));
  }

  private MessageDTO edit(Long replyId) {
    return messageService.edit(chat.getId(), replyId, ana, "at two");
  }

  private void deleteOriginal() {
    messageService.delete(chat.getId(), original.getId(), rui);
  }

  private static void assertWaiting(Future<?> other) {
    assertThrows(TimeoutException.class, () -> other.get(WAITING_MILLIS, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("Deleting the original during a reply waits for it, then clears the link")
  void deleteWaitsForTheReply() throws Exception {
    // Arrange
    Long[] replyId = new Long[1];
    Future<?>[] deleted = new Future<?>[1];

    // Act - the reply is sent but not committed when the delete starts
    transaction.executeWithoutResult(status -> {
      replyId[0] = reply().id();
      deleted[0] = otherThread.submit(this::deleteOriginal);
      assertWaiting(deleted[0]);
    });
    deleted[0].get(5, TimeUnit.SECONDS);

    // Assert
    Message saved = messageRepository.findById(replyId[0]).orElseThrow();
    assertTrue(saved.isReply());
    assertNull(saved.getReplyTo());
    assertFalse(messageRepository.existsById(original.getId()));
  }

  @Test
  @DisplayName("Replying while the original is being deleted waits, then it's REPLIED_MESSAGE_NOT_FOUND and not a 500")
  void replyWaitsForTheDelete() throws Exception {
    // Arrange
    Future<?>[] replied = new Future<?>[1];

    // Act - the delete reached the database but isn't committed when the reply starts
    transaction.executeWithoutResult(status -> {
      deleteOriginal();
      messageRepository.flush();
      replied[0] = otherThread.submit(this::reply);
      assertWaiting(replied[0]);
    });

    // Assert
    ExecutionException e = assertThrows(ExecutionException.class, () -> replied[0].get(5, TimeUnit.SECONDS));
    StudyMatchException refused = assertInstanceOf(StudyMatchException.class, e.getCause());
    assertEquals(ErrorCode.REPLIED_MESSAGE_NOT_FOUND, refused.getCode());
  }

  @Test
  @DisplayName("Editing a reply whose original was deleted meanwhile doesn't put the link back")
  void editDoesNotPutTheLinkBack() {
    // Arrange
    Long replyId = reply().id();

    // Act - the reply is loaded with its original, which is gone by the time the edit saves
    transaction.executeWithoutResult(status -> {
      Message loaded = messageRepository.findByIdAndChat(replyId, chat).orElseThrow();
      Future<?> deleted = otherThread.submit(this::deleteOriginal);
      try {
        deleted.get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      loaded.edit("at two");
    });

    // Assert
    Message saved = messageRepository.findById(replyId).orElseThrow();
    assertEquals("at two", saved.getText());
    assertNull(saved.getReplyTo());
  }

  @Test
  @DisplayName("Deleting the original while a reply is edited waits for the edit, then clears the link")
  void deleteWaitsForTheEdit() throws Exception {
    // Arrange
    Long replyId = reply().id();
    Future<?>[] deleted = new Future<?>[1];

    // Act - the edit is done but not committed when the delete starts
    transaction.executeWithoutResult(status -> {
      MessageDTO edited = edit(replyId);
      assertEquals("lunch?", edited.replyTo().text());
      deleted[0] = otherThread.submit(this::deleteOriginal);
      assertWaiting(deleted[0]);
    });
    deleted[0].get(5, TimeUnit.SECONDS);

    // Assert
    Message saved = messageRepository.findById(replyId).orElseThrow();
    assertEquals("at two", saved.getText());
    assertNull(saved.getReplyTo());
  }

  @Test
  @DisplayName("Editing a reply while its original is being deleted waits, and what's sent has no quote")
  void editWaitsForTheDelete() throws Exception {
    // Arrange
    Long replyId = reply().id();
    List<Future<MessageDTO>> edited = new ArrayList<>();

    // Act - the delete reached the database but isn't committed when the edit starts
    transaction.executeWithoutResult(status -> {
      deleteOriginal();
      messageRepository.flush();
      edited.add(otherThread.submit(() -> edit(replyId)));
      assertWaiting(edited.getFirst());
    });

    // Assert
    MessageDTO sent = edited.getFirst().get(5, TimeUnit.SECONDS);
    assertEquals("at two", sent.text());
    assertTrue(sent.reply());
    assertNull(sent.replyTo());
  }

}
