package com.studymatch.dtos;

// the graded student's row; weightsComplete false means the grade is saved but there's no final grade yet
public record GradeResponseDTO(GradeSheetDTO.Row student, boolean weightsComplete) {
}
