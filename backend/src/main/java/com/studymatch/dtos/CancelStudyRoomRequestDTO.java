package com.studymatch.dtos;

import jakarta.validation.constraints.Size;

// cancel StudyRoom (#53): the reason is optional
public record CancelStudyRoomRequestDTO(@Size(max = 500) String reason) {
}
