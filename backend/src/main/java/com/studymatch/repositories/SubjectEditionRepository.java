package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;

public interface SubjectEditionRepository extends JpaRepository<SubjectEdition, Long> {

  boolean existsBySubjectAndAcademicYear(Subject subject, AcademicYear academicYear);

}
