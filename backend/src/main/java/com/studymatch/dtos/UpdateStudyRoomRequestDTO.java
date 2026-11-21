package com.studymatch.dtos;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// edit StudyRoom (#51): everything but the subject can change
public record UpdateStudyRoomRequestDTO(
    @NotNull LocalDateTime startsAt,
    @NotNull LocalDateTime endsAt,
    @Size(max = 500) String description,
    @NotNull @Min(2) @Max(20) Integer capacity,
    // null asks the server to create a new Meet link
    @Pattern(regexp = "^https://meet\\.google\\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}(\\?.*)?$") String link) {

}
