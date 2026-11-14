package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;

import jakarta.persistence.LockModeType;

public interface SubjectEditionRepository extends JpaRepository<SubjectEdition, Long> {

  boolean existsBySubjectAndAcademicYear(Subject subject, AcademicYear academicYear);

  boolean existsByIdAndTeachers_Number(Long id, Long teacherNumber);

  List<SubjectEdition> findDistinctByTeachers_Number(Long teacherNumber);

  List<SubjectEdition> findByAcademicYear(AcademicYear academicYear);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<SubjectEdition> findLockedById(Long id);

}
