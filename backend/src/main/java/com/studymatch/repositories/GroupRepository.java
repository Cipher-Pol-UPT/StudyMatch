package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.GroupStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

  // analytics: the members come with their groups, not one select per group
  @EntityGraph(attributePaths = { "members", "members.degree", "members.personalityProfile", "assignment",
      "assignment.groupingRule", "assignment.subjectEdition", "assignment.subjectEdition.subject" })
  List<Group> findByAssignment_SubjectEdition_AcademicYearAndStatus(AcademicYear academicYear, GroupStatus status);

}
