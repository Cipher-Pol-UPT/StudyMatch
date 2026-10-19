package com.studymatch.dtos;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStudyRoomRequestDTO(
    @NotNull Long subjectId,
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    @Size(max = 500) String description,
    @NotNull @Min(2) @Max(20) Integer capacity,
    @NotBlank @Pattern(regexp = "^https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}(\\?.*)?$") String link) {

}
