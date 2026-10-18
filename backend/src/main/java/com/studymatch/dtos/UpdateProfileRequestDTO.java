package com.studymatch.dtos;

import java.util.Set;

import com.studymatch.enums.Language;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequestDTO(String banner, @Size(max = 500) String aboutMe,
    @NotNull Set<@NotBlank @Size(max = 50) String> skills, @NotNull Set<@NotBlank @Size(max = 50) String> interests,
    @NotNull Set<Language> languages) {

}
