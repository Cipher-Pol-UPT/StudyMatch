package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.enums.GroupStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

  List<Group> findByAssignment_SubjectEdition_AcademicYearAndStatus(AcademicYear academicYear, GroupStatus status);

}
