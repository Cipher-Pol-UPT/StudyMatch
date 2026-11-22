package com.studymatch.dtos;

// what's pushed when a message is deleted, the open chat drops it and the chat list reloads that chat
public record MessageDeletedDTO(Long chatId, Long messageId) {
}
