package com.studymatch.dtos;

import java.util.List;

// a page of an open chat, oldest first, with the reactions to its messages. hasMore is false once the first message
// is in
public record MessagePageDTO(List<MessageDTO> messages, List<ReactionDTO> reactions, boolean hasMore) {
}
