package com.studymatch.dtos;

// chatId is null while there's no DM yet, the first message creates it
public record DirectChatDTO(Long chatId, ChatContactDTO contact) {

}
