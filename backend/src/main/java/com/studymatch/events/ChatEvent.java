package com.studymatch.events;

import com.studymatch.enums.ChatEventType;

// something changed in a chat, pushed to everyone who has it open once the transaction commits
public record ChatEvent(Long chatId, ChatEventType type, Object payload) {
}
