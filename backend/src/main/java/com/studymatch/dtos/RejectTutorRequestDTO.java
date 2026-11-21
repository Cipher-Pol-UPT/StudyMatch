package com.studymatch.dtos;

import jakarta.validation.constraints.Size;

// approve tutor application (#81): the rejection reason is optional
public record RejectTutorRequestDTO(@Size(max = 500) String reason) {
}
