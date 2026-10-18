package com.studymatch.dtos;

import com.studymatch.security.PasswordPolicy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// currentPassword is left out on the forced first change, the service decides when it's needed
public record ChangePasswordRequestDTO(
    String currentPassword,
    @NotBlank
    @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    @Pattern(regexp = PasswordPolicy.PATTERN)
    String newPassword) {
}
