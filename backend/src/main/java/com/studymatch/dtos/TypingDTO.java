package com.studymatch.dtos;

// who is typing in a chat, never saved
public record TypingDTO(Long chatId, ChatContactDTO user) {
}
