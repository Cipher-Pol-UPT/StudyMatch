package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.GroupService;

@RestController
@RequestMapping("/groups/")
public class GroupController {

  private final GroupService groupService;

  public GroupController(GroupService groupService) {
    this.groupService = groupService;
  }

}
