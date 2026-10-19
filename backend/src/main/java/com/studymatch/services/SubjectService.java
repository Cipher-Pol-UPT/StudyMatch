package com.studymatch.services;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.DegreePlacementDTO;
import com.studymatch.dtos.SubjectRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.models.Degree;
import com.studymatch.models.Subject;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.SubjectRepository;

@Service
public class SubjectService {

  private final SubjectRepository subjectRepository;
  private final CategoryRepository categoryRepository;
  private final DegreeRepository degreeRepository;

  public SubjectService(SubjectRepository subjectRepository, CategoryRepository categoryRepository,
      DegreeRepository degreeRepository) {
    this.subjectRepository = subjectRepository;
    this.categoryRepository = categoryRepository;
    this.degreeRepository = degreeRepository;
  }

  // "MAT1" and " mat1 " are the same subject
  @Transactional
  public Subject registerSubject(SubjectRequestDTO request) {
    String code = request.code().trim();
    if (subjectRepository.existsByCodeIgnoreCase(code)) {
      throw new StudyMatchException(ErrorCode.SUBJECT_ALREADY_EXISTS);
    }

    Category category = categoryRepository.findById(request.categoryId())
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    Subject subject = new Subject(code, request.name().trim(), request.ects(), category);
    for (DegreePlacementDTO placement : request.degrees()) {
      Degree degree = degreeRepository.findById(placement.degreeId())
          .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
      if (placement.curricularYear() > degree.getCurricularYears()) {
        throw new StudyMatchException(ErrorCode.CURRICULAR_YEAR_OUT_OF_RANGE);
      }
      subject.addDegree(degree, placement.curricularYear(), placement.semester());
    }

    // two Staff registering the same code at once both pass the check above, the unique column stops the second
    try {
      return subjectRepository.saveAndFlush(subject);
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.SUBJECT_ALREADY_EXISTS);
    }
  }

  public List<Subject> listSubjects() {
    return subjectRepository.findAllByOrderByCodeAsc();
  }

  public Subject getSubject(Long id) {
    return subjectRepository.findWithDegreesById(id)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

}
