package com.studymatch.dtos;

import com.studymatch.models.Degree;

public record DegreeDTO(Long id, String name, int years) {

  public static DegreeDTO from(Degree degree) {
    return new DegreeDTO(degree.getId(), degree.getName(), degree.getYears());
  }

}
