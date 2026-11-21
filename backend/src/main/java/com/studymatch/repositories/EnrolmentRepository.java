package com.studymatch.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

  List<Enrolment> findByStudent(Student student);

  List<Enrolment> findBySubjectEditionId(Long subjectEditionId);

  // analytics: each student comes with their profile in the same select, not one select per student
  @EntityGraph(attributePaths = { "student", "student.degree", "student.personalityProfile", "classSection",
      "subjectEdition", "subjectEdition.subject", "subjectEdition.subject.category" })
  List<Enrolment> findBySubjectEdition_AcademicYear(AcademicYear academicYear);
  // analytics: every group member's finished subjects at once, with the ECTS to weigh them
  @EntityGraph(attributePaths = { "student", "subjectEdition", "subjectEdition.subject" })
  List<Enrolment> findByStudentInAndFinalGradeIsNotNull(Collection<Student> students);

  List<Enrolment> findByClassSection(ClassSection classSection);

  Optional<Enrolment> findBySubjectEditionIdAndStudent_Number(Long subjectEditionId, Long studentNumber);

  List<Enrolment> findBySubjectEdition_AcademicYearAndFinalGradeIsNull(AcademicYear academicYear);

  boolean existsByStudent(Student student);

  boolean existsByStudentAndSubjectEdition(Student student, SubjectEdition subjectEdition);

  boolean existsByStudentAndSubjectEdition_SubjectAndStatus(Student student, Subject subject, EnrolmentStatus status);

  boolean existsByStudentAndSubjectEdition_Teachers_Number(Student student, Long teacherNumber);

  // join StudyRoom (#45): the student is enrolled in the subject's edition for that academic year
  boolean existsByStudent_NumberAndSubjectEdition_SubjectAndSubjectEdition_AcademicYear(
      Long studentNumber, Subject subject, AcademicYear academicYear);

  boolean existsByStudent_NumberAndSubjectEdition_SubjectAndStatus(
      Long studentNumber, Subject subject, EnrolmentStatus status);

}
