package com.studymatch.dtos;

import java.util.List;

import com.studymatch.models.PersonalityProfile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonalityTestRequestDTO(
    @NotNull @Size(min = PersonalityProfile.QUESTION_COUNT, max = PersonalityProfile.QUESTION_COUNT)
    List<@NotNull @Min(1) @Max(5) Integer> answers) {

}
