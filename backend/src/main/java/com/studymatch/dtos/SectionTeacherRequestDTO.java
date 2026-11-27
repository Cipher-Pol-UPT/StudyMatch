package com.studymatch.dtos;

import jakarta.validation.constraints.NotNull;

// the teacher (by number) who teaches the class section, one of the edition's teachers
public record SectionTeacherRequestDTO(@NotNull Long teacherNumber) {
}
