package com.studymatch.dtos;

import java.util.Comparator;
import java.util.List;

import com.studymatch.models.Subject;

public record SubjectResponseDTO(Long id, String code, String name, int ects, CategoryResponseDTO category,
    List<DegreePlacementResponseDTO> degrees) {

  // the subject keeps its degrees in a set, so sort them to always answer in the same order
  private static final Comparator<DegreePlacementResponseDTO> PLACEMENT_ORDER = Comparator
      .comparingInt(DegreePlacementResponseDTO::curricularYear)
      .thenComparingInt(DegreePlacementResponseDTO::semester)
      .thenComparing(DegreePlacementResponseDTO::degreeName);

  public static SubjectResponseDTO from(Subject subject) {
    List<DegreePlacementResponseDTO> degrees = subject.getDegreeSubjects().stream()
        .map(DegreePlacementResponseDTO::from)
        .sorted(PLACEMENT_ORDER)
        .toList();
    return new SubjectResponseDTO(subject.getId(), subject.getCode(), subject.getName(), subject.getEcts(),
        CategoryResponseDTO.from(subject.getCategory()), degrees);
  }

}
