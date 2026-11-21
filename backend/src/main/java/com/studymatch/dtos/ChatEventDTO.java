package com.studymatch.dtos;

import com.studymatch.enums.ChatEventType;

// what the websocket pushes, the payload depends on the type (a message, a chat...)
public record ChatEventDTO(ChatEventType type, Object payload) {
}
