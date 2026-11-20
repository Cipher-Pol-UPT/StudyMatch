package com.studymatch.dtos;

import java.util.List;

// shown before Staff starts a new year. currentStartYear is null when there's no year yet
public record AcademicYearPreviewDTO(int nextStartYear, Integer currentStartYear,
    List<UnfinishedEnrolmentDTO> unfinishedEnrolments) {

}
