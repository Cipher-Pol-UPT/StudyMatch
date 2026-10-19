package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// the subject's edition for one academic year: who teaches it and its classes (classes are optional)
public record SubjectEditionRequestDTO(
    @NotNull Integer academicYear,
    @NotEmpty List<@NotNull Long> teacherIds,
    List<@NotBlank @Size(max = 20) String> classSections) {

  public List<String> classSectionsOrEmpty() {
    return classSections == null ? List.of() : classSections;
  }

}
