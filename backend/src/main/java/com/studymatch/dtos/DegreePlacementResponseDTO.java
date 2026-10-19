package com.studymatch.dtos;

import com.studymatch.models.DegreeSubject;

public record DegreePlacementResponseDTO(Long degreeId, String degreeName, int curricularYear, int semester) {

  public static DegreePlacementResponseDTO from(DegreeSubject degreeSubject) {
    return new DegreePlacementResponseDTO(degreeSubject.getDegree().getId(), degreeSubject.getDegree().getName(),
        degreeSubject.getCurricularYear(), degreeSubject.getSemester());
  }

}
