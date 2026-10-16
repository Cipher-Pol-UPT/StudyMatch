package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

  List<Enrolment> findByStudent(Student student);

  boolean existsByStudentAndSubjectEdition_Teachers_Number(Student student, Long teacherNumber);

}
