package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

  List<Enrolment> findByStudent(Student student);

  List<Enrolment> findBySubjectEditionId(Long subjectEditionId);

  Optional<Enrolment> findBySubjectEditionIdAndStudent_Number(Long subjectEditionId, Long studentNumber);

  boolean existsByStudent(Student student);

  boolean existsByStudentAndSubjectEdition(Student student, SubjectEdition subjectEdition);

  boolean existsByStudentAndSubjectEdition_SubjectAndStatus(Student student, Subject subject, EnrolmentStatus status);

  boolean existsByStudentAndSubjectEdition_Teachers_Number(Student student, Long teacherNumber);

}
