package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// code and category can't change, they define the students' category levels.
// The teachers are the current edition's; null leaves them as they are, an empty list gets past here on purpose:
// the edition refuses it with AT_LEAST_ONE_TEACHER_REQUIRED.
// The weekly sessions replace the whole plan, null leaves it as it is and an empty list clears it
public record UpdateSubjectRequestDTO(
    @NotBlank @Size(max = 100) String name,
    @NotNull @Min(1) @Max(60) Integer ects,
    List<@NotNull Long> teacherNumbers,
    List<@Valid @NotNull WeeklySessionDTO> weeklySessions) {

}
