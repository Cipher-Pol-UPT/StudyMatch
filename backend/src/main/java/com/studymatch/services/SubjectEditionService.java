package com.studymatch.services;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;

@Service
public class SubjectEditionService {

  private final SubjectEditionRepository subjectEditionRepository;
  private final SubjectRepository subjectRepository;
  private final AcademicYearRepository academicYearRepository;
  private final TeacherRepository teacherRepository;

  public SubjectEditionService(SubjectEditionRepository subjectEditionRepository, SubjectRepository subjectRepository,
      AcademicYearRepository academicYearRepository, TeacherRepository teacherRepository) {
    this.subjectEditionRepository = subjectEditionRepository;
    this.subjectRepository = subjectRepository;
    this.academicYearRepository = academicYearRepository;
    this.teacherRepository = teacherRepository;
  }

  // a new edition of a subject that's already registered, e.g. the same subject in the next academic year
  @Transactional
  public SubjectEdition registerEdition(Long subjectId, SubjectEditionRequestDTO request) {
    Subject subject = subjectRepository.findWithDegreesById(subjectId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    return createEdition(subject, request);
  }

  // one edition per subject per academic year; the caller's transaction covers it
  public SubjectEdition createEdition(Subject subject, SubjectEditionRequestDTO request) {
    AcademicYear academicYear = academicYearRepository.findById(request.academicYear())
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    if (subjectEditionRepository.existsBySubjectAndAcademicYear(subject, academicYear)) {
      throw new StudyMatchException(ErrorCode.SUBJECT_EDITION_ALREADY_EXISTS);
    }

    // the same teacher picked twice counts once, but every id must be a real teacher
    Set<Long> teacherIds = new HashSet<>(request.teacherIds());
    List<Teacher> teachers = teacherRepository.findAllById(teacherIds);
    if (teachers.size() != teacherIds.size()) {
      throw new StudyMatchException(ErrorCode.NOT_FOUND);
    }

    SubjectEdition edition = new SubjectEdition(subject, academicYear, new HashSet<>(teachers));
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

}
