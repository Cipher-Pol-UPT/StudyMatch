package com.studymatch.dtos;

// chatId is null while there's no DM yet, the first message creates it.
// blocked means you blocked them, the DM stays hidden (null) until you unblock
public record DirectChatDTO(Long chatId, ChatContactDTO contact, boolean blocked) {

}
