package com.studymatch.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// currentPassword is left out on the forced first change, the service decides when it's needed
public record ChangePasswordRequestDTO(
    String currentPassword,
    @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "(?=.*\\p{L})(?=.*\\d).*") String newPassword) {
}
