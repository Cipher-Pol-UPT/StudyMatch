package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.enums.GroupStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

  List<Group> findByAssignmentAndStatusOrderByNumber(Assignment assignment, GroupStatus status);

  boolean existsByAssignmentAndStatus(Assignment assignment, GroupStatus status);

  void deleteByAssignmentAndStatus(Assignment assignment, GroupStatus status);

  // analytics: the members come with their groups, not one select per group
  @EntityGraph(attributePaths = { "members", "members.degree", "members.personalityProfile", "assignment",
      "assignment.groupingRule", "assignment.subjectEdition", "assignment.subjectEdition.subject" })
  List<Group> findByAssignment_SubjectEdition_AcademicYearAndStatus(AcademicYear academicYear, GroupStatus status);

  // view my groups: filtered through a second join, so the fetched members are the whole group, not just the student
  @Query("""
      select distinct g from Group g join g.members student
        left join fetch g.members
        join fetch g.assignment a join fetch a.subjectEdition e join fetch e.subject
      where student.number = :studentNumber and g.status = :status""")
  List<Group> findWithMember(Long studentNumber, GroupStatus status);

}
