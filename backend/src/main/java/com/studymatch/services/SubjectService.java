package com.studymatch.services;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.DegreePlacementDTO;
import com.studymatch.dtos.SubjectEditionRequestDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.SubjectRepository;

@Service
public class SubjectService {

  private final SubjectRepository subjectRepository;
  private final CategoryService categoryService;
  private final DegreeService degreeService;
  private final SubjectEditionService subjectEditionService;

  public SubjectService(SubjectRepository subjectRepository, CategoryService categoryService,
      DegreeService degreeService, SubjectEditionService subjectEditionService) {
    this.subjectRepository = subjectRepository;
    this.categoryService = categoryService;
    this.degreeService = degreeService;
    this.subjectEditionService = subjectEditionService;
  }

  // "MAT1" and " mat1 " are the same subject. The subject and its first edition are saved together:
  // if the edition is refused, the subject isn't kept either
  @Transactional
  public SubjectEdition registerSubject(SubjectRequestDTO request) {
    String code = request.code().trim();
    // the id lets the frontend offer a new edition of the existing subject instead
    subjectRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
      throw new StudyMatchException(ErrorCode.SUBJECT_ALREADY_EXISTS, Map.of("id", existing.getId()));
    });

    Category category = categoryService.getCategory(request.categoryId());
    Subject subject = new Subject(code, request.name().trim(), request.ects(), category);
    for (DegreePlacementDTO placement : request.degrees()) {
      Degree degree = degreeService.getDegree(placement.degreeId());
      if (placement.curricularYear() > degree.getCurricularYears()) {
        throw new StudyMatchException(ErrorCode.CURRICULAR_YEAR_OUT_OF_RANGE);
      }
      subject.addDegree(degree, placement.curricularYear(), placement.semester());
    }

    // two Staff registering the same code at once both pass the check above, the unique column stops the second.
    // The failed insert aborts the transaction, so the other subject's id can't be looked up here
    try {
      subject = subjectRepository.saveAndFlush(subject);
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.SUBJECT_ALREADY_EXISTS);
    }
    return subjectEditionService.createEdition(subject, request.edition());
  }

  // a new edition of a subject that's already registered, e.g. the same subject in the next academic year
  @Transactional
  public SubjectEdition registerEdition(Long subjectId, SubjectEditionRequestDTO request) {
    return subjectEditionService.createEdition(getSubject(subjectId), request);
  }

  public List<Subject> listSubjects() {
    return subjectRepository.findAllByOrderByCodeAsc();
  }

  public Subject getSubject(Long id) {
    return subjectRepository.findWithDegreesById(id)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

}
