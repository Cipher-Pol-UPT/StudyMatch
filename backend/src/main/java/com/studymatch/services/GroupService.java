package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.enums.GroupStatus;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Group;
import com.studymatch.repositories.GroupRepository;

@Service
public class GroupService {

  private final GroupRepository groupRepository;

  public GroupService(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

  // analytics: drafts can still change, so only the published groups are measured
  public List<Group> listPublishedGroups(AcademicYear academicYear) {
    return groupRepository.findByAssignment_SubjectEdition_AcademicYearAndStatus(academicYear, GroupStatus.PUBLISHED);
  }

}
