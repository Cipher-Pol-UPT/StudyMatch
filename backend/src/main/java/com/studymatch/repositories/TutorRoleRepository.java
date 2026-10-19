package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Student;
import com.studymatch.models.TutorRole;

public interface TutorRoleRepository extends JpaRepository<TutorRole, Long> {

  List<TutorRole> findByStudent(Student student);

  Optional<TutorRole> findByStudentNumberAndSubjectId(Long studentNumber, Long subjectId);

}
