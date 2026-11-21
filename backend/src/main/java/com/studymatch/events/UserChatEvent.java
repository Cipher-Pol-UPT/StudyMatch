package com.studymatch.events;

import com.studymatch.enums.ChatEventType;
import com.studymatch.models.User;

// something changed in one user's chat list (a new chat, one they left...), pushed only to them
public record UserChatEvent(User recipient, ChatEventType type, Object payload) {
}
