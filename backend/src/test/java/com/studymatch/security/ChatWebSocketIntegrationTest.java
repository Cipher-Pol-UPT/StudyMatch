package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.studymatch.enums.ChatEventType;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.ChatSettingsRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;

// a real server and a real STOMP client, connecting like the browser will
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

  private static final String PASSWORD = "Test1234";
  private static final long WAIT_SECONDS = 5;

  @LocalServerPort
  private int port;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private DegreeRepository degreeRepository;

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private StaffRepository staffRepository;

  @Autowired
  private ChatRepository chatRepository;

  @Autowired
  private MessageRepository messageRepository;

  @Autowired
  private ChatSettingsRepository chatSettingsRepository;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private TransactionTemplate transactionTemplate;

  private WebSocketStompClient stompClient;
  private Degree degree;
  private Student ana;
  private Student rui;
  private Student eva;
  private Staff staff;
  private DirectChat chat;

  // ana and rui share a DM, eva isn't in it. a real server commits, so everything is deleted after each test
  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new CompositeMessageConverter(
        List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));

    degree = degreeRepository.save(new Degree("IT WebSocket Degree", 3));
    ana = saveStudent("it.ws.ana@example.com", "Ana Test");
    rui = saveStudent("it.ws.rui@example.com", "Rui Test");
    eva = saveStudent("it.ws.eva@example.com", "Eva Test");
    staff = new Staff("it.ws.staff@example.com", "it.ws.staff@studymatch.com", "Staff Test",
        passwordEncoder.encode(PASSWORD));
    staff.setMustChangePassword(false);
    staff = staffRepository.save(staff);
    chat = chatRepository.save(new DirectChat(ana, rui));
  }

  @AfterEach
  void tearDown() {
    stompClient.stop();
    messageRepository.deleteAll(messageRepository.findAll().stream()
        .filter(message -> message.getChat().getId().equals(chat.getId()))
        .toList());
    chatSettingsRepository.deleteAll(chatSettingsRepository.findAll().stream()
        .filter(settings -> settings.getChat().getId().equals(chat.getId()))
        .toList());
    chatRepository.delete(chat);
    studentRepository.deleteAll(List.of(ana, rui, eva));
    staffRepository.delete(staff);
    degreeRepository.delete(degree);
  }

  private Student saveStudent(String personalEmail, String name) {
    Student student = new Student(personalEmail, name, passwordEncoder.encode(PASSWORD), degree, 1);
    student.setMustChangePassword(false);
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5, 0.5));
    student = studentRepository.save(student);
    student.generateInstitutionalEmail("studymatch.com");
    return studentRepository.save(student);
  }

  // logs in over HTTP and returns the cookies (session + XSRF-TOKEN), like the browser holds after login
  private Map<String, String> login(String email) throws Exception {
    CookieManager cookies = new CookieManager();
    HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
    http.send(HttpRequest.newBuilder(url("/users/me")).GET().build(), HttpResponse.BodyHandlers.discarding());

    HttpResponse<Void> response = http.send(HttpRequest.newBuilder(url("/users/login"))
        .header("Content-Type", "application/json")
        .header("X-XSRF-TOKEN", cookie(cookies, "XSRF-TOKEN"))
        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
        .build(), HttpResponse.BodyHandlers.discarding());
    assertEquals(200, response.statusCode());

    // the token can change on login, so the latest one is read again
    http.send(HttpRequest.newBuilder(url("/users/me")).GET().build(), HttpResponse.BodyHandlers.discarding());
    return cookies.getCookieStore().getCookies().stream()
        .collect(Collectors.toMap(c -> c.getName(), c -> c.getValue(), (a, b) -> b));
  }

  private static String cookie(CookieManager cookies, String name) {
    return cookies.getCookieStore().getCookies().stream()
        .filter(c -> c.getName().equals(name))
        .findFirst()
        .orElseThrow()
        .getValue();
  }

  private URI url(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  // a POST, PUT or DELETE with the login's cookies and CSRF header, like client.ts sends it
  private int send(String method, Map<String, String> cookies, String path, String body) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(url(path))
        .header("Cookie", cookies.entrySet().stream()
            .map(c -> c.getKey() + "=" + c.getValue())
            .collect(Collectors.joining("; ")))
        .header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private StompSession connect(Map<String, String> cookies, boolean withCsrf, BlockingQueue<String> errors)
      throws Exception {
    WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
    handshake.add("Cookie", cookies.entrySet().stream()
        .map(c -> c.getKey() + "=" + c.getValue())
        .collect(Collectors.joining("; ")));
    StompHeaders connect = new StompHeaders();
    if (withCsrf) {
      connect.add("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
    }
    return stompClient.connectAsync("ws://localhost:" + port + "/ws", handshake, connect, new ErrorCollector(errors))
        .get(WAIT_SECONDS, TimeUnit.SECONDS);
  }

  private StompSession connect(Student student) throws Exception {
    return connect(login(student.getInstitutionalEmail()), true, new LinkedBlockingQueue<>());
  }

  private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
    BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
    session.subscribe(destination, new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return Map.class;
      }

      @Override
      @SuppressWarnings("unchecked")
      public void handleFrame(StompHeaders headers, Object payload) {
        received.add((Map<String, Object>) payload);
      }
    });
    return received;
  }

  private void publishCommitted(Object event) {
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
  }

  // the subscription is registered a moment after SUBSCRIBE is sent, so it publishes until something arrives
  private Map<String, Object> publishUntilReceived(Object event, BlockingQueue<Map<String, Object>> received)
      throws InterruptedException {
    for (int attempt = 0; attempt < 20; attempt++) {
      publishCommitted(event);
      Map<String, Object> frame = received.poll(250, TimeUnit.MILLISECONDS);
      if (frame != null) {
        return frame;
      }
    }
    return null;
  }

  // collects ERROR frames, the server's answer to a refused frame
  private static class ErrorCollector extends StompSessionHandlerAdapter {

    private final BlockingQueue<String> errors;

    ErrorCollector(BlockingQueue<String> errors) {
      this.errors = errors;
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return String.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      errors.add(String.valueOf(headers.getFirst("message")));
    }

  }

  @Test
  @DisplayName("A member subscribed to the chat gets its events once the transaction commits")
  void memberReceivesChatEvent() throws Exception {
    // Arrange
    StompSession session = connect(ana);
    BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/chats/" + chat.getId());

    // Act
    Map<String, Object> frame = publishUntilReceived(
        new ChatEvent(chat.getId(), ChatEventType.MESSAGE_CREATED, Map.of("text", "hello")), received);

    // Assert
    assertNotNull(frame);
    assertEquals("MESSAGE_CREATED", frame.get("type"));
    assertEquals(Map.of("text", "hello"), frame.get("payload"));
  }

  @Test
  @DisplayName("Nothing is pushed for a transaction that rolls back")
  void rolledBackEventIsNotPushed() throws Exception {
    // Arrange - a first event proves the subscription works
    StompSession session = connect(ana);
    BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/chats/" + chat.getId());
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), received));
    received.clear();

    // Act
    transactionTemplate.executeWithoutResult(status -> {
      eventPublisher.publishEvent(new ChatEvent(chat.getId(), ChatEventType.MESSAGE_CREATED, Map.of("text", "x")));
      status.setRollbackOnly();
    });

    // Assert
    assertNull(received.poll(1, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("A user's chat list events only reach that user")
  void userQueueOnlyReachesTheUser() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> toAna = subscribe(connect(ana), "/user/queue/chats");
    BlockingQueue<Map<String, Object>> toRui = subscribe(connect(rui), "/user/queue/chats");

    // Act
    Map<String, Object> frame = publishUntilReceived(
        new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of("id", chat.getId().intValue())), toAna);

    // Assert
    assertNotNull(frame);
    assertEquals("CHAT_UPDATED", frame.get("type"));
    assertNull(toRui.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("Someone who isn't in the chat can't subscribe to it")
  void nonMemberSubscribeIsRefused() throws Exception {
    // Arrange
    BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    StompSession session = connect(login(eva.getInstitutionalEmail()), true, errors);

    // Act
    BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/chats/" + chat.getId());
    publishCommitted(new ChatEvent(chat.getId(), ChatEventType.MESSAGE_CREATED, Map.of("text", "secret")));

    // Assert
    assertNotNull(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS));
    assertNull(received.poll(500, TimeUnit.MILLISECONDS));
  }

  @ParameterizedTest
  @ValueSource(strings = { "/topic/chats/*", "/topic/chats/**", "/topic/chats/{id}", "/topic/chats/?", "/topic/**" })
  @DisplayName("Someone who isn't in the chat can't listen to it with a pattern instead of the id")
  void nonMemberPatternSubscribeIsRefused(String destination) throws Exception {
    // Arrange
    BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    StompSession session = connect(login(eva.getInstitutionalEmail()), true, errors);

    // Act - publishes for a while, a pattern that got through would have received something by then
    BlockingQueue<Map<String, Object>> received = subscribe(session, destination);
    Map<String, Object> frame = publishUntilReceived(
        new ChatEvent(chat.getId(), ChatEventType.MESSAGE_CREATED, Map.of("text", "secret")), received);

    // Assert
    assertNull(frame);
    assertNotNull(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("A message sent through REST reaches the other member: in the open chat and in their chat list")
  void sentMessageIsPushed() throws Exception {
    // Arrange - a first event on each proves Rui's subscriptions are registered
    StompSession session = connect(rui);
    BlockingQueue<Map<String, Object>> chatTopic = subscribe(session, "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> chatList = subscribe(session, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), chatTopic));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), chatList));
    chatTopic.clear();
    chatList.clear();

    // Act
    int status = send("POST", login(ana.getInstitutionalEmail()), "/chats/" + chat.getId() + "/messages",
        "{\"text\":\"hi Rui\"}");

    // Assert
    assertEquals(201, status);
    Map<String, Object> created = chatTopic.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(created);
    assertEquals("MESSAGE_CREATED", created.get("type"));
    assertEquals("hi Rui", ((Map<?, ?>) created.get("payload")).get("text"));
    Map<String, Object> updated = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(updated);
    assertEquals("CHAT_UPDATED", updated.get("type"));
    assertEquals(chat.getId().intValue(), ((Map<?, ?>) updated.get("payload")).get("chatId"));
  }

  @Test
  @DisplayName("Marking a chat read tells the user's other tabs, so they clear its badge")
  void markReadIsPushedToTheUser() throws Exception {
    // Arrange
    StompSession session = connect(ana);
    BlockingQueue<Map<String, Object>> chatList = subscribe(session, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of()), chatList));
    chatList.clear();

    // Act
    int status = send("PUT", login(ana.getInstitutionalEmail()), "/chats/" + chat.getId() + "/read", "");

    // Assert
    assertEquals(204, status);
    Map<String, Object> read = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(read);
    assertEquals("CHAT_READ", read.get("type"));
    assertEquals(chat.getId().intValue(), read.get("payload"));
  }

  @Test
  @DisplayName("An edit reaches the open chat and the chat list with the new text, as an edit, not a new message (GR10)")
  void editedMessageIsPushed() throws Exception {
    // Arrange
    Message message = messageRepository.save(new Message(chat, ana, "see you at 10"));
    StompSession session = connect(rui);
    BlockingQueue<Map<String, Object>> chatTopic = subscribe(session, "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> chatList = subscribe(session, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), chatTopic));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), chatList));
    chatTopic.clear();
    chatList.clear();

    // Act
    int status = send("PUT", login(ana.getInstitutionalEmail()),
        "/chats/" + chat.getId() + "/messages/" + message.getId(), "{\"text\":\"see you at 11\"}");

    // Assert
    assertEquals(200, status);
    Map<String, Object> edited = chatTopic.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(edited);
    assertEquals("MESSAGE_EDITED", edited.get("type"));
    Map<?, ?> payload = (Map<?, ?>) edited.get("payload");
    assertEquals(message.getId().intValue(), payload.get("id"));
    assertEquals("see you at 11", payload.get("text"));
    assertNotNull(payload.get("editedAt"));
    Map<String, Object> preview = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(preview);
    assertEquals("MESSAGE_EDITED", preview.get("type"));
    assertEquals("see you at 11", ((Map<?, ?>) preview.get("payload")).get("text"));
    assertNull(chatList.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("A delete reaches the open chat and the chat list, so the unread count can drop (GR10)")
  void deletedMessageIsPushed() throws Exception {
    // Arrange
    Message message = messageRepository.save(new Message(chat, ana, "see you at 10"));
    StompSession session = connect(rui);
    BlockingQueue<Map<String, Object>> chatTopic = subscribe(session, "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> chatList = subscribe(session, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), chatTopic));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), chatList));
    chatTopic.clear();
    chatList.clear();

    // Act
    int status = send("DELETE", login(ana.getInstitutionalEmail()),
        "/chats/" + chat.getId() + "/messages/" + message.getId(), "");

    // Assert
    assertEquals(204, status);
    for (BlockingQueue<Map<String, Object>> received : List.of(chatTopic, chatList)) {
      Map<String, Object> deleted = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(deleted);
      assertEquals("MESSAGE_DELETED", deleted.get("type"));
      Map<?, ?> payload = (Map<?, ?>) deleted.get("payload");
      assertEquals(chat.getId().intValue(), payload.get("chatId"));
      assertEquals(message.getId().intValue(), payload.get("messageId"));
    }
  }

  @Test
  @DisplayName("Staff can't open the websocket (GR8)")
  void staffCantConnect() throws Exception {
    // Arrange
    Map<String, String> cookies = login(staff.getInstitutionalEmail());

    // Act + Assert - the handshake gets a 403, so the connection never opens
    assertThrows(ExecutionException.class, () -> connect(cookies, true, new LinkedBlockingQueue<>()));
  }

  @Test
  @DisplayName("Without logging in the websocket doesn't open")
  void loggedOutCantConnect() {
    // Act + Assert
    assertThrows(ExecutionException.class, () -> connect(Map.of(), true, new LinkedBlockingQueue<>()));
  }

  @Test
  @DisplayName("A CONNECT without the CSRF token is refused")
  void connectWithoutCsrfIsRefused() throws Exception {
    // Arrange
    Map<String, String> cookies = login(ana.getInstitutionalEmail());
    BlockingQueue<String> errors = new LinkedBlockingQueue<>();

    // Act + Assert - the server answers with an ERROR frame instead of CONNECTED
    assertThrows(Exception.class, () -> connect(cookies, false, errors));
  }

}
