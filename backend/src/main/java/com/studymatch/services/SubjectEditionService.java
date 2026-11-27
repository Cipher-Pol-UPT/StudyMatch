package com.studymatch.services;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

  // update subject (staff): the subject's edition this year, the only one whose teachers can still change
  public SubjectEdition getCurrentEdition(Subject subject) {
    return findCurrentEdition(subject)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // apply for tutoring (#41): empty when the subject isn't taught in the current academic year
  public Optional<SubjectEdition> findCurrentEdition(Subject subject) {
    return findCurrentYear()
        .flatMap(year -> subjectEditionRepository.findBySubjectAndAcademicYear(subject, year));
  }

  // apply for tutoring (#41): the newest year, fetched once so a list doesn't look it up per subject
  public Optional<AcademicYear> findCurrentYear() {
    return academicYearService.findCurrent();
  }

  // apply for tutoring (#41): whether the subject has an edition for a specific year
  public boolean hasEdition(Subject subject, AcademicYear year) {
    return subjectEditionRepository.existsBySubjectAndAcademicYear(subject, year);
  }

  // past editions keep their teachers. With no teachers the model refuses it (AT_LEAST_ONE_TEACHER_REQUIRED).
  // Locked so a section can't get a teacher this removes at the same moment
  @Transactional
  public SubjectEdition updateCurrentTeachers(Subject subject, List<Long> teacherNumbers) {
    SubjectEdition edition = findCurrentYear()
        .flatMap(year -> subjectEditionRepository.findLockedBySubjectAndAcademicYear(subject, year))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    edition.setTeachers(teacherService.getTeachers(teacherNumbers));
    return edition;
  }

  // teacher of a class section (staff): only this year's, past lessons already had their teacher
  @Transactional
  public SubjectEditionResponseDTO assignSectionTeacher(Long editionId, String classSection, Long teacherNumber) {
    SubjectEdition edition = getLockedCurrentEdition(editionId);
    edition.assignTeacher(classSection, teacherService.getTeacher(teacherNumber));
    return SubjectEditionResponseDTO.from(edition);
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

  // enrol students (staff): only the current year's editions can take students
  @Transactional(readOnly = true)
  public List<SubjectEditionResponseDTO> listCurrent() {
    return academicYearService.findCurrent()
        .map(year -> subjectEditionRepository.findByAcademicYear(year).stream()
            .map(SubjectEditionResponseDTO::from)
            .sorted(Comparator.comparing((SubjectEditionResponseDTO edition) -> edition.subject().name())
                .thenComparing(SubjectEditionResponseDTO::id))
            .toList())
        .orElse(List.of());
  }

  // analytics: the year's editions, the filter offers them even before anyone enrols
  public List<SubjectEdition> listEditions(AcademicYear academicYear) {
    return subjectEditionRepository.findByAcademicYear(academicYear);
  }

  // block account (admin): a teacher's editions this year need another teacher once they're blocked
  @Transactional(readOnly = true)
  public List<SubjectEditionResponseDTO> listCurrentTaughtBy(Long teacherNumber) {
    return academicYearService.findCurrent()
        .map(year -> subjectEditionRepository.findDistinctByTeachers_NumberAndAcademicYear(teacherNumber, year)
            .stream()
            .map(SubjectEditionResponseDTO::from)
            .sorted(Comparator.comparing((SubjectEditionResponseDTO edition) -> edition.subject().name())
                .thenComparing(SubjectEditionResponseDTO::id))
            .toList())
        .orElse(List.of());
  }

  public SubjectEdition getLockedCurrentEdition(Long editionId) {
    SubjectEdition edition = getLockedEdition(editionId);
    if (!academicYearService.isCurrent(edition.getAcademicYear())) {
      throw new StudyMatchException(ErrorCode.SUBJECT_EDITION_NOT_CURRENT);
    }
    return edition;
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

  public SubjectEdition getEdition(Long editionId) {
    return subjectEditionRepository.findById(editionId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // locked until the caller's transaction ends, so changes to the edition happen one at a time
  public SubjectEdition getLockedEdition(Long editionId) {
    return subjectEditionRepository.findLockedById(editionId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

}
