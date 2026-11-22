package com.studymatch.events;

import com.studymatch.enums.ChatEventType;
import com.studymatch.models.User;

// a chat change only one member may see (a DM hidden from a blocker), pushed to that member's open chat only
public record MemberChatEvent(User recipient, Long chatId, ChatEventType type, Object payload) {
}
