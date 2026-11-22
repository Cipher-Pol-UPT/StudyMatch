package com.studymatch.utils;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.studymatch.dtos.ChatEventDTO;
import com.studymatch.events.ChatEvent;
import com.studymatch.events.MemberChatEvent;
import com.studymatch.events.UserChatEvent;

// pushes only after the commit, so a rolled back change is never seen
@Component
public class ChatEventRelay {

  private final SimpMessagingTemplate messagingTemplate;

  public ChatEventRelay(SimpMessagingTemplate messagingTemplate) {
    this.messagingTemplate = messagingTemplate;
  }

  @TransactionalEventListener
  public void pushToChat(ChatEvent event) {
    messagingTemplate.convertAndSend("/topic/chats/" + event.chatId(), new ChatEventDTO(event.type(), event.payload()));
  }

  // the user's login is their institutional email, that's who spring sends /user/queue/chats to
  @TransactionalEventListener
  public void pushToUser(UserChatEvent event) {
    messagingTemplate.convertAndSendToUser(event.recipient().getInstitutionalEmail(), "/queue/chats",
        new ChatEventDTO(event.type(), event.payload()));
  }

  // same as the chat's topic, but only that user's tabs get it (GR9)
  @TransactionalEventListener
  public void pushToMember(MemberChatEvent event) {
    messagingTemplate.convertAndSendToUser(event.recipient().getInstitutionalEmail(),
        "/queue/chats/" + event.chatId(), new ChatEventDTO(event.type(), event.payload()));
  }

}
