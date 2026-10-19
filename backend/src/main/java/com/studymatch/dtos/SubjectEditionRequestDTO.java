package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// the subject's edition for one academic year: who teaches it (by teacher number) and its classes (optional).
// An empty teacher list gets past here on purpose: the edition itself refuses it with AT_LEAST_ONE_TEACHER_REQUIRED
public record SubjectEditionRequestDTO(
    @NotNull Integer academicYear,
    @NotNull List<@NotNull Long> teacherNumbers,
    List<@NotBlank @Size(max = 20) String> classSections) {

  public List<String> classSectionsOrEmpty() {
    return classSections == null ? List.of() : classSections;
  }

}
