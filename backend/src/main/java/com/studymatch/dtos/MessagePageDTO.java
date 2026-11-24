package com.studymatch.dtos;

import java.util.List;

// a page of an open chat, oldest first, with the reactions to its messages. hasMore is false once the first message
// is in, hasNewer once the latest is. the side you scrolled from always has more, so that one stays true
public record MessagePageDTO(List<MessageDTO> messages, List<ReactionDTO> reactions, boolean hasMore,
    boolean hasNewer) {
}
