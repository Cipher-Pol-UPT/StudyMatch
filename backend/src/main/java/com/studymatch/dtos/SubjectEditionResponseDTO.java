package com.studymatch.dtos;

import java.util.Comparator;
import java.util.List;

import com.studymatch.models.ClassSection;
import com.studymatch.models.SubjectEdition;

public record SubjectEditionResponseDTO(Long id, int academicYear, SubjectResponseDTO subject,
    List<TeacherSummaryDTO> teachers, List<String> classSections) {

  // teachers and classes are kept in sets, so sort them to always answer in the same order
  public static SubjectEditionResponseDTO from(SubjectEdition edition) {
    List<TeacherSummaryDTO> teachers = edition.getTeachers().stream()
        .map(TeacherSummaryDTO::from)
        .sorted(Comparator.comparing(TeacherSummaryDTO::name))
        .toList();
    List<String> classSections = edition.getClassSections().stream()
        .map(ClassSection::getCode)
        .sorted()
        .toList();
    return new SubjectEditionResponseDTO(edition.getId(), edition.getAcademicYear().getStartYear(),
        SubjectResponseDTO.from(edition.getSubject()), teachers, classSections);
  }

}
