package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.GroupStatus;
import com.studymatch.models.Assignment;
import com.studymatch.models.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

  List<Group> findByAssignmentAndStatusOrderByNumber(Assignment assignment, GroupStatus status);

  boolean existsByAssignmentAndStatus(Assignment assignment, GroupStatus status);

  void deleteByAssignmentAndStatus(Assignment assignment, GroupStatus status);

}
