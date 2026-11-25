package com.studymatch.dtos;

import java.util.List;

// a page of a chat's search results, newest first. no reactions, the panel doesn't show them
public record MessageSearchDTO(List<MessageDTO> messages, boolean hasMore) {
}
