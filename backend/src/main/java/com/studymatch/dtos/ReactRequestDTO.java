package com.studymatch.dtos;

import jakarta.validation.constraints.NotNull;

// any single emoji, the service checks it's one
public record ReactRequestDTO(@NotNull String emoji) {
}
