package com.studymatch.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
import com.studymatch.events.MemberChatEvent;
import com.studymatch.events.UserChatEvent;
import com.studymatch.models.CustomChat;
import com.studymatch.models.Degree;
import com.studymatch.models.DirectChat;
import com.studymatch.models.Message;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.UserBlock;
import com.studymatch.repositories.ChatRepository;
import com.studymatch.repositories.ChatSettingsRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.MessageRepository;
import com.studymatch.repositories.StaffRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.UserBlockRepository;

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
  private UserBlockRepository userBlockRepository;

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Autowired
  private TransactionTemplate transactionTemplate;

  private WebSocketStompClient stompClient;
  private final Set<BlockingQueue<Map<String, Object>>> ready = ConcurrentHashMap.newKeySet();
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
    chatRepository.deleteAll(chatRepository.findCustomChatsOf(ana.getNumber()));
    userBlockRepository.deleteAll(userBlockRepository.findAll().stream()
        .filter(block -> block.getBlocker().getNumber().equals(rui.getNumber()))
        .toList());
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

  // a GET with the login's cookies, the body as text
  private HttpResponse<String> get(Map<String, String> cookies, String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(url(path))
        .header("Cookie", cookies.entrySet().stream()
            .map(c -> c.getKey() + "=" + c.getValue())
            .collect(Collectors.joining("; ")))
        .GET()
        .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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

  // once a queue's probe came back, an empty payload is a probe sent before that landing late, it'd be in the
  // queue after the test clears it. no real event has an empty payload.
  // so probe each queue once: a second publishUntilReceived with Map.of() on it gets dropped and fails
  private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
    BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
    session.subscribe(destination, new StompFrameHandler() {
      @Override
      public Type getPayloadType(StompHeaders headers) {
        return Map.class;
      }

      @Override
      @SuppressWarnings("unchecked")
      public void handleFrame(StompHeaders headers, Object payload) {
        Map<String, Object> frame = (Map<String, Object>) payload;
        if (ready.contains(received) && Map.of().equals(frame.get("payload"))) {
          return;
        }
        received.add(frame);
      }
    });
    return received;
  }

  private void publishCommitted(Object event) {
    transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
  }

  // the subscription is registered a moment after SUBSCRIBE is sent, so it publishes until something arrives.
  // on a slow runner an earlier one can still be on its way, subscribe drops it
  private Map<String, Object> publishUntilReceived(Object event, BlockingQueue<Map<String, Object>> received)
      throws InterruptedException {
    for (int attempt = 0; attempt < 20; attempt++) {
      publishCommitted(event);
      Map<String, Object> frame = received.poll(250, TimeUnit.MILLISECONDS);
      if (frame != null) {
        ready.add(received);
        return frame;
      }
    }
    return null;
  }

  // the student's chat list, ready to listen: a first event proves the subscription is registered
  private BlockingQueue<Map<String, Object>> chatListOf(Student student) throws Exception {
    BlockingQueue<Map<String, Object>> chatList = subscribe(connect(student), "/user/queue/chats");
    assertNotNull(publishUntilReceived(new UserChatEvent(student, ChatEventType.CHAT_UPDATED, Map.of()), chatList));
    chatList.clear();
    return chatList;
  }

  // the online list changes once the server handles the connect or disconnect, a moment after the client's side
  private String awaitOnline(Map<String, String> cookies, String expected) throws Exception {
    String body = null;
    for (int attempt = 0; attempt < 20 && !expected.equals(body); attempt++) {
      if (attempt > 0) {
        TimeUnit.MILLISECONDS.sleep(250);
      }
      body = get(cookies, "/chats/online").body();
    }
    return body;
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
    // Arrange - eva shares no chat with ana, so neither one going online pushes anything to the other
    BlockingQueue<Map<String, Object>> toAna = subscribe(connect(ana), "/user/queue/chats");
    BlockingQueue<Map<String, Object>> toEva = subscribe(connect(eva), "/user/queue/chats");

    // Act
    Map<String, Object> frame = publishUntilReceived(
        new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of("id", chat.getId().intValue())), toAna);

    // Assert
    assertNotNull(frame);
    assertEquals("CHAT_UPDATED", frame.get("type"));
    assertNull(toEva.poll(500, TimeUnit.MILLISECONDS));
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
  @DisplayName("A new group chat reaches each member's chat list as its row, before anyone writes")
  void createdGroupChatIsPushed() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> chatList = chatListOf(rui);

    // Act
    int status = send("POST", login(ana.getInstitutionalEmail()), "/chats/groups",
        "{\"name\":\"Study group\",\"memberNumbers\":[" + rui.getNumber() + "," + eva.getNumber() + "]}");

    // Assert
    assertEquals(201, status);
    Map<String, Object> created = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(created);
    assertEquals("CHAT_ADDED", created.get("type"));
    Map<?, ?> row = (Map<?, ?>) created.get("payload");
    assertEquals("Study group", row.get("name"));
    assertNull(row.get("lastMessage"));
  }

  @Test
  @DisplayName("Someone added to a group chat gets it in their chat list with its last message and nothing unread")
  void addedMemberIsPushed() throws Exception {
    // Arrange - ana, rui and zeca have a group chat with a message, eva is added to it
    Student zeca = saveStudent("it.ws.zeca@example.com", "Zeca Test");
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, zeca)));
    try {
      messageRepository.save(new Message(group, ana, "before eva"));
      BlockingQueue<Map<String, Object>> chatList = chatListOf(eva);

      // Act
      int status = send("POST", login(ana.getInstitutionalEmail()), "/chats/" + group.getId() + "/members",
          "{\"memberNumbers\":[" + eva.getNumber() + "]}");

      // Assert
      assertEquals(200, status);
      Map<String, Object> added = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(added);
      assertEquals("CHAT_ADDED", added.get("type"));
      Map<?, ?> row = (Map<?, ?>) added.get("payload");
      assertEquals(group.getId().intValue(), row.get("chatId"));
      assertEquals("before eva", ((Map<?, ?>) row.get("lastMessage")).get("text"));
      assertEquals(0, row.get("unread"));
    } finally {
      deleteGroupChat(group);
      studentRepository.delete(zeca);
    }
  }

  // a group chat made in a test, with what points at it
  private void deleteGroupChat(CustomChat group) {
    messageRepository.deleteAll(messageRepository.findAll().stream()
        .filter(message -> message.getChat().getId().equals(group.getId()))
        .toList());
    chatSettingsRepository.deleteAll(chatSettingsRepository.findAll().stream()
        .filter(settings -> settings.getChat().getId().equals(group.getId()))
        .toList());
    chatRepository.delete(group);
  }

  @Test
  @DisplayName("Someone removed from a group chat gets CHAT_REMOVED on their chat list")
  void removedMemberIsTold() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    try {
      BlockingQueue<Map<String, Object>> chatList = chatListOf(rui);

      // Act
      int status = send("DELETE", login(ana.getInstitutionalEmail()),
          "/chats/" + group.getId() + "/members/" + rui.getNumber(), "");

      // Assert
      assertEquals(200, status);
      Map<String, Object> removed = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(removed);
      assertEquals("CHAT_REMOVED", removed.get("type"));
      assertEquals(group.getId().intValue(), removed.get("payload"));
    } finally {
      deleteGroupChat(group);
    }
  }

  @Test
  @DisplayName("Leaving a group chat tells the user's other tabs with CHAT_REMOVED")
  void leaverIsTold() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    try {
      BlockingQueue<Map<String, Object>> chatList = chatListOf(rui);

      // Act
      int status = send("POST", login(rui.getInstitutionalEmail()), "/chats/" + group.getId() + "/leave", "");

      // Assert
      assertEquals(204, status);
      Map<String, Object> removed = chatList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(removed);
      assertEquals("CHAT_REMOVED", removed.get("type"));
      assertEquals(group.getId().intValue(), removed.get("payload"));
    } finally {
      deleteGroupChat(group);
    }
  }

  @Test
  @DisplayName("The last one out deletes the chat and its messages, each leave committing on its own")
  void lastOneOutDeletesCommitted() throws Exception {
    // Arrange
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    try {
      messageRepository.save(new Message(group, ana, "hi all"));
      assertEquals(204, send("POST", login(rui.getInstitutionalEmail()), "/chats/" + group.getId() + "/leave", ""));
      assertEquals(204, send("POST", login(ana.getInstitutionalEmail()), "/chats/" + group.getId() + "/leave", ""));

      // Act
      int status = send("POST", login(eva.getInstitutionalEmail()), "/chats/" + group.getId() + "/leave", "");

      // Assert
      assertEquals(204, status);
      assertTrue(chatRepository.findById(group.getId()).isEmpty());
      assertTrue(messageRepository.findAll().stream()
          .noneMatch(message -> message.getChat().getId().equals(group.getId())));
    } finally {
      if (chatRepository.existsById(group.getId())) {
        deleteGroupChat(group);
      }
    }
  }

  @Test
  @DisplayName("A removed member's open tab stops getting the group chat's messages and typing, the others still do (GR8)")
  void removedMemberStopsHearing() throws Exception {
    // Arrange - rui and eva have the chat open, a first event proves each subscription is registered
    CustomChat group = chatRepository.save(new CustomChat("Study group", null, ana, List.of(rui, eva)));
    try {
      StompSession ruiTab = connect(rui);
      BlockingQueue<Map<String, Object>> ruiTopic = subscribe(ruiTab, "/topic/chats/" + group.getId());
      BlockingQueue<Map<String, Object>> ruiQueue = subscribe(ruiTab, "/user/queue/chats/" + group.getId());
      BlockingQueue<Map<String, Object>> evaQueue = subscribe(connect(eva), "/user/queue/chats/" + group.getId());
      assertNotNull(publishUntilReceived(new ChatEvent(group.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
      assertNotNull(publishUntilReceived(
          new MemberChatEvent(rui, group.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiQueue));
      assertNotNull(publishUntilReceived(
          new MemberChatEvent(eva, group.getId(), ChatEventType.CHAT_UPDATED, Map.of()), evaQueue));
      ruiTopic.clear();
      ruiQueue.clear();
      evaQueue.clear();
      Map<String, String> anaLogin = login(ana.getInstitutionalEmail());
      assertEquals(200, send("DELETE", anaLogin, "/chats/" + group.getId() + "/members/" + rui.getNumber(), ""));

      // Act
      assertEquals(201, send("POST", anaLogin, "/chats/" + group.getId() + "/messages", "{\"text\":\"rui's gone\"}"));
      StompSession anaTab = connect(ana);
      anaTab.send("/app/chats/" + group.getId() + "/typing", "");

      // Assert - eva gets both, by then rui would have too
      Map<String, Object> created = evaQueue.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(created);
      assertEquals("MESSAGE_CREATED", created.get("type"));
      Map<String, Object> typing = evaQueue.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(typing);
      assertEquals("TYPING", typing.get("type"));
      assertNull(ruiTopic.poll(500, TimeUnit.MILLISECONDS));
      assertNull(ruiQueue.poll(500, TimeUnit.MILLISECONDS));
    } finally {
      deleteGroupChat(group);
    }
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
  @DisplayName("A reply quoting a hidden message reaches both, with the quote only for its author (GR9)")
  void hiddenQuoteOnlyReachesItsAuthor() throws Exception {
    // Arrange - Rui had blocked Ana and unblocked since, what she sent meanwhile stays hidden from him
    Message hidden = new Message(chat, ana, "see you at 10");
    hidden.hideFrom(rui);
    hidden = messageRepository.save(hidden);
    StompSession ruiSession = connect(rui);
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(ruiSession, "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> ruiChat = subscribe(ruiSession, "/user/queue/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> ruiList = subscribe(ruiSession, "/user/queue/chats");
    BlockingQueue<Map<String, Object>> anaChat = subscribe(connect(ana), "/user/queue/chats/" + chat.getId());
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    assertNotNull(publishUntilReceived(
        new MemberChatEvent(rui, chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiChat));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), ruiList));
    assertNotNull(publishUntilReceived(
        new MemberChatEvent(ana, chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), anaChat));
    ruiTopic.clear();
    ruiChat.clear();
    ruiList.clear();
    anaChat.clear();

    // Act
    int status = send("POST", login(ana.getInstitutionalEmail()), "/chats/" + chat.getId() + "/messages",
        "{\"text\":\"so?\",\"replyToId\":" + hidden.getId() + "}");

    // Assert
    assertEquals(201, status);
    Map<String, Object> toAna = anaChat.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(toAna);
    Map<?, ?> quote = (Map<?, ?>) ((Map<?, ?>) toAna.get("payload")).get("replyTo");
    assertEquals("see you at 10", quote.get("text"));
    for (BlockingQueue<Map<String, Object>> ofRui : List.of(ruiChat, ruiList)) {
      Map<String, Object> toRui = ofRui.poll(WAIT_SECONDS, TimeUnit.SECONDS);
      assertNotNull(toRui);
      Map<?, ?> message = (Map<?, ?>) toRui.get("payload");
      assertEquals("so?", message.get("text"));
      assertEquals(true, message.get("reply"));
      assertNull(message.get("replyTo"));
    }
    assertNull(ruiTopic.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("An edit of a message hidden from the blocker reaches only the sender, not the blocker's tab or list (GR9)")
  void hiddenEditSkipsTheBlocker() throws Exception {
    // Arrange - Rui has the DM open before blocking Ana, her message after the block is hidden from him
    StompSession ruiSession = connect(rui);
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(ruiSession, "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> ruiList = subscribe(ruiSession, "/user/queue/chats");
    StompSession anaSession = connect(ana);
    BlockingQueue<Map<String, Object>> anaChat = subscribe(anaSession, "/user/queue/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> anaList = subscribe(anaSession, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), ruiList));
    assertNotNull(publishUntilReceived(
        new MemberChatEvent(ana, chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), anaChat));
    assertNotNull(publishUntilReceived(new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of()), anaList));
    ruiTopic.clear();
    ruiList.clear();
    anaChat.clear();
    anaList.clear();
    userBlockRepository.save(new UserBlock(rui, ana));
    Message hidden = new Message(chat, ana, "see you at 10");
    hidden.hideFrom(rui);
    hidden = messageRepository.save(hidden);

    // Act
    int status = send("PUT", login(ana.getInstitutionalEmail()),
        "/chats/" + chat.getId() + "/messages/" + hidden.getId(), "{\"text\":\"see you at 11\"}");

    // Assert
    assertEquals(200, status);
    Map<String, Object> edited = anaChat.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(edited);
    assertEquals("MESSAGE_EDITED", edited.get("type"));
    assertEquals("see you at 11", ((Map<?, ?>) edited.get("payload")).get("text"));
    Map<String, Object> preview = anaList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(preview);
    assertEquals("MESSAGE_EDITED", preview.get("type"));
    assertNull(ruiTopic.poll(1, TimeUnit.SECONDS));
    assertNull(ruiList.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("Deleting a message hidden from the blocker tells only the sender, not the blocker's tab or list (GR9)")
  void hiddenDeleteSkipsTheBlocker() throws Exception {
    // Arrange - Rui has the DM open before blocking Ana, her message after the block is hidden from him
    StompSession ruiSession = connect(rui);
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(ruiSession, "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> ruiList = subscribe(ruiSession, "/user/queue/chats");
    StompSession anaSession = connect(ana);
    BlockingQueue<Map<String, Object>> anaChat = subscribe(anaSession, "/user/queue/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> anaList = subscribe(anaSession, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), ruiList));
    assertNotNull(publishUntilReceived(
        new MemberChatEvent(ana, chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), anaChat));
    assertNotNull(publishUntilReceived(new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of()), anaList));
    ruiTopic.clear();
    ruiList.clear();
    anaChat.clear();
    anaList.clear();
    userBlockRepository.save(new UserBlock(rui, ana));
    Message hidden = new Message(chat, ana, "see you at 10");
    hidden.hideFrom(rui);
    hidden = messageRepository.save(hidden);

    // Act
    int status = send("DELETE", login(ana.getInstitutionalEmail()),
        "/chats/" + chat.getId() + "/messages/" + hidden.getId(), "");

    // Assert
    assertEquals(204, status);
    Map<String, Object> deleted = anaChat.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(deleted);
    assertEquals("MESSAGE_DELETED", deleted.get("type"));
    assertEquals(hidden.getId().intValue(), ((Map<?, ?>) deleted.get("payload")).get("messageId"));
    Map<String, Object> listed = anaList.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(listed);
    assertEquals("MESSAGE_DELETED", listed.get("type"));
    assertNull(ruiTopic.poll(1, TimeUnit.SECONDS));
    assertNull(ruiList.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("Someone who blocked the other member can't subscribe to their DM while the block lasts (GR9)")
  void blockerSubscribeIsRefused() throws Exception {
    // Arrange
    userBlockRepository.save(new UserBlock(rui, ana));
    BlockingQueue<String> errors = new LinkedBlockingQueue<>();
    StompSession session = connect(login(rui.getInstitutionalEmail()), true, errors);

    // Act
    BlockingQueue<Map<String, Object>> received = subscribe(session, "/topic/chats/" + chat.getId());
    publishCommitted(new ChatEvent(chat.getId(), ChatEventType.MESSAGE_CREATED, Map.of("text", "hi")));

    // Assert
    assertNotNull(errors.poll(WAIT_SECONDS, TimeUnit.SECONDS));
    assertNull(received.poll(500, TimeUnit.MILLISECONDS));
  }

  @Test
  @DisplayName("A blocked user's DM updates their own chat list but never the blocker's (GR9)")
  void blockedMessageSkipsTheBlocker() throws Exception {
    // Arrange - a first event on each proves both subscriptions are registered
    userBlockRepository.save(new UserBlock(rui, ana));
    BlockingQueue<Map<String, Object>> toAna = subscribe(connect(ana), "/user/queue/chats");
    BlockingQueue<Map<String, Object>> toRui = subscribe(connect(rui), "/user/queue/chats");
    assertNotNull(publishUntilReceived(new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of()), toAna));
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), toRui));
    toAna.clear();
    toRui.clear();

    // Act
    int status = send("POST", login(ana.getInstitutionalEmail()), "/chats/" + chat.getId() + "/messages",
        "{\"text\":\"hi Rui\"}");

    // Assert
    assertEquals(201, status);
    Map<String, Object> updated = toAna.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(updated);
    assertEquals("CHAT_UPDATED", updated.get("type"));
    assertNull(toRui.poll(1, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("A blocker's tab subscribed before the block doesn't get the blocked user's DM, only the sender does (GR9)")
  void blockedMessageSkipsTheTopic() throws Exception {
    // Arrange - Rui opens the DM, then blocks Ana. a first event on each proves both subscriptions are registered
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(connect(rui), "/topic/chats/" + chat.getId());
    BlockingQueue<Map<String, Object>> anaChat = subscribe(connect(ana), "/user/queue/chats/" + chat.getId());
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    assertNotNull(publishUntilReceived(
        new MemberChatEvent(ana, chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), anaChat));
    ruiTopic.clear();
    anaChat.clear();
    userBlockRepository.save(new UserBlock(rui, ana));

    // Act
    int status = send("POST", login(ana.getInstitutionalEmail()), "/chats/" + chat.getId() + "/messages",
        "{\"text\":\"hi Rui\"}");

    // Assert
    assertEquals(201, status);
    Map<String, Object> created = anaChat.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(created);
    assertEquals("MESSAGE_CREATED", created.get("type"));
    assertEquals("hi Rui", ((Map<?, ?>) created.get("payload")).get("text"));
    assertNull(ruiTopic.poll(1, TimeUnit.SECONDS));
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

  @Test
  @DisplayName("A member typing reaches whoever has the chat open, with who is typing")
  void typingReachesTheChat() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(connect(rui), "/topic/chats/" + chat.getId());
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    ruiTopic.clear();
    StompSession anaSession = connect(ana);

    // Act
    anaSession.send("/app/chats/" + chat.getId() + "/typing", "");

    // Assert
    Map<String, Object> typing = ruiTopic.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(typing);
    assertEquals("TYPING", typing.get("type"));
    Map<?, ?> payload = (Map<?, ?>) typing.get("payload");
    assertEquals(chat.getId(), ((Number) payload.get("chatId")).longValue());
    Map<?, ?> user = (Map<?, ?>) payload.get("user");
    assertEquals(ana.getNumber(), ((Number) user.get("number")).longValue());
    assertEquals("Ana Test", user.get("name"));
  }

  @Test
  @DisplayName("Typing in a chat you're not in is dropped (GR8)")
  void nonMemberTypingIsDropped() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(connect(rui), "/topic/chats/" + chat.getId());
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    ruiTopic.clear();
    StompSession evaSession = connect(eva);

    // Act
    evaSession.send("/app/chats/" + chat.getId() + "/typing", "");

    // Assert
    assertNull(ruiTopic.poll(1, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("Opening the app puts you online for the people you share a chat with, nobody else")
  void onlineReachesContactsOnly() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> toRui = chatListOf(rui);
    BlockingQueue<Map<String, Object>> toEva = chatListOf(eva);

    // Act
    connect(ana);

    // Assert
    Map<String, Object> online = toRui.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(online);
    assertEquals("USER_ONLINE", online.get("type"));
    assertEquals(ana.getNumber(), ((Number) online.get("payload")).longValue());
    assertNull(toEva.poll(1, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("A second tab doesn't put you online again")
  void secondTabPushesNothing() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> toRui = chatListOf(rui);
    connect(ana);
    assertNotNull(toRui.poll(WAIT_SECONDS, TimeUnit.SECONDS));

    // Act
    connect(ana);

    // Assert
    assertNull(toRui.poll(1, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("You go offline when your last tab closes, not before")
  void lastTabClosingPushesOffline() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> toRui = chatListOf(rui);
    StompSession firstTab = connect(ana);
    StompSession secondTab = connect(ana);
    assertNotNull(toRui.poll(WAIT_SECONDS, TimeUnit.SECONDS));

    // Act
    firstTab.disconnect();
    Map<String, Object> afterFirst = toRui.poll(1, TimeUnit.SECONDS);
    secondTab.disconnect();

    // Assert
    assertNull(afterFirst);
    Map<String, Object> offline = toRui.poll(WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(offline);
    assertEquals("USER_OFFLINE", offline.get("type"));
    assertEquals(ana.getNumber(), ((Number) offline.get("payload")).longValue());
  }

  @Test
  @DisplayName("The online list has the people you share a chat with who have the app open, and drops them on close")
  void onlineContactsList() throws Exception {
    // Arrange - eva is online too, but shares no chat with rui
    StompSession anaSession = connect(ana);
    connect(eva);
    Map<String, String> ruiCookies = login(rui.getInstitutionalEmail());

    // Act
    String whileOpen = awaitOnline(ruiCookies, "[" + ana.getNumber() + "]");
    anaSession.disconnect();
    String afterClose = awaitOnline(ruiCookies, "[]");

    // Assert
    assertEquals("[" + ana.getNumber() + "]", whileOpen);
    assertEquals("[]", afterClose);
  }

  @Test
  @DisplayName("Staff can't ask who is online (GR8)")
  void staffCantListOnline() throws Exception {
    // Arrange
    Map<String, String> cookies = login(staff.getInstitutionalEmail());

    // Act
    HttpResponse<String> response = get(cookies, "/chats/online");

    // Assert
    assertEquals(403, response.statusCode());
  }

  @Test
  @DisplayName("A blocker's tab opened before the block doesn't see the blocked user typing (GR9)")
  void blockedTypingSkipsTheBlocker() throws Exception {
    // Arrange
    BlockingQueue<Map<String, Object>> ruiTopic = subscribe(connect(rui), "/topic/chats/" + chat.getId());
    assertNotNull(publishUntilReceived(new ChatEvent(chat.getId(), ChatEventType.CHAT_UPDATED, Map.of()), ruiTopic));
    ruiTopic.clear();
    userBlockRepository.save(new UserBlock(rui, ana));
    StompSession anaSession = connect(ana);

    // Act
    anaSession.send("/app/chats/" + chat.getId() + "/typing", "");

    // Assert
    assertNull(ruiTopic.poll(1, TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("A blocker and the person they blocked don't get each other's online pushes (GR9)")
  void blockHidesOnlineBothWays() throws Exception {
    // Arrange - ana is online, rui isn't yet
    userBlockRepository.save(new UserBlock(rui, ana));
    StompSession anaSession = connect(ana);
    BlockingQueue<Map<String, Object>> toAna = subscribe(anaSession, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new UserChatEvent(ana, ChatEventType.CHAT_UPDATED, Map.of()), toAna));
    toAna.clear();

    // Act - rui's first tab would tell ana he's online, ana's last tab closing would tell rui she's offline
    StompSession ruiSession = connect(rui);
    Map<String, Object> ruiOnline = toAna.poll(1, TimeUnit.SECONDS);
    BlockingQueue<Map<String, Object>> toRui = subscribe(ruiSession, "/user/queue/chats");
    assertNotNull(publishUntilReceived(new UserChatEvent(rui, ChatEventType.CHAT_UPDATED, Map.of()), toRui));
    toRui.clear();
    anaSession.disconnect();

    // Assert
    assertNull(ruiOnline);
    assertNull(toRui.poll(1, TimeUnit.SECONDS));
  }

}
