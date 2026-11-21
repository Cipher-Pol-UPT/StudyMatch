package com.studymatch.dtos;

import java.util.List;

// a page of an open chat, oldest first. hasMore is false once the first message is in
public record MessagePageDTO(List<MessageDTO> messages, boolean hasMore) {
}
