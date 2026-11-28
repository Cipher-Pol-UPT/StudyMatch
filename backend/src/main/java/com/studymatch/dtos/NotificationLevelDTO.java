package com.studymatch.dtos;

import com.studymatch.enums.NotificationLevel;

import jakarta.validation.constraints.NotNull;

// how much a member hears from one chat, the chat's menu reads and changes it (GR11)
public record NotificationLevelDTO(@NotNull NotificationLevel level) {
}
