package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.GroupRepository;

@Service
public class GroupService {

  private final GroupRepository groupRepository;

  public GroupService(GroupRepository groupRepository) {
    this.groupRepository = groupRepository;
  }

}
