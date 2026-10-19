package com.studymatch.dtos;

import com.studymatch.models.Degree;

public record DegreeResponseDTO(Long id, String name, int curricularYears) {

  public static DegreeResponseDTO from(Degree degree) {
    return new DegreeResponseDTO(degree.getId(), degree.getName(), degree.getCurricularYears());
  }

}
