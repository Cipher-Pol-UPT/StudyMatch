package com.studymatch.dtos;

import java.util.List;

// one row of the CSV as the staff will see it in the preview, no errors = can be imported
public record ImportPreviewRowDTO(int row, RegisterStudentRequestDTO student, List<FieldErrorDTO> errors) {
}
