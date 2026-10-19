package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;

public interface SubjectEditionRepository extends JpaRepository<SubjectEdition, Long> {

  boolean existsBySubjectAndAcademicYear(Subject subject, AcademicYear academicYear);

  boolean existsByIdAndTeachers_Number(Long id, Long teacherNumber);

  List<SubjectEdition> findDistinctByTeachers_Number(Long teacherNumber);

}

