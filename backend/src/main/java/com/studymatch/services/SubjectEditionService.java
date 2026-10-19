package com.studymatch.services;

import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.dtos.SubjectEditionResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.SubjectEditionRepository;

@Service
public class SubjectEditionService {

  private final SubjectEditionRepository subjectEditionRepository;
  private final AcademicYearService academicYearService;
  private final TeacherService teacherService;

  public SubjectEditionService(SubjectEditionRepository subjectEditionRepository,
      AcademicYearService academicYearService, TeacherService teacherService) {
    this.subjectEditionRepository = subjectEditionRepository;
    this.academicYearService = academicYearService;
    this.teacherService = teacherService;
  }

  // one edition per subject per academic year. With no teachers the model refuses it
  // (AT_LEAST_ONE_TEACHER_REQUIRED)
  @Transactional
  public SubjectEdition createEdition(Subject subject, SubjectEditionRequestDTO request) {
    AcademicYear academicYear = academicYearService.getAcademicYear(request.academicYear());
    if (subjectEditionRepository.existsBySubjectAndAcademicYear(subject, academicYear)) {
      throw new StudyMatchException(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS);
    }

    SubjectEdition edition = new SubjectEdition(subject, academicYear,
        teacherService.getTeachers(request.teacherNumbers()));
    for (String code : request.classSectionsOrEmpty()) {
      edition.addClassSection(code.trim());
    }

    // two Staff creating the same edition at once both pass the check above, the unique constraint stops the second
    try {
      return subjectEditionRepository.saveAndFlush(edition);
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS);
    }
  }

  // discover the editions assigned to the authenticated teacher
  @Transactional(readOnly = true)
  public List<SubjectEditionResponseDTO> listTaughtBy(Long teacherNumber) {
    // Map the lazy classes, teachers and subject degrees while the transaction is open.
    return subjectEditionRepository.findDistinctByTeachers_Number(teacherNumber).stream()
        .map(SubjectEditionResponseDTO::from)
        .sorted(Comparator.comparingInt(SubjectEditionResponseDTO::academicYear).reversed()
            .thenComparing(edition -> edition.subject().name())
            .thenComparing(SubjectEditionResponseDTO::id))
        .toList();
  }

  // a teacher only sees the students of editions they teach
  public void checkTaughtBy(Long editionId, Long teacherNumber) {
    if (!subjectEditionRepository.existsById(editionId)) {
      throw new StudyMatchException(ErrorCode.NOT_FOUND);
    }
    if (!subjectEditionRepository.existsByIdAndTeachers_Number(editionId, teacherNumber)) {
      throw new StudyMatchException(ErrorCode.ACCESS_DENIED);
    }
  }

}
