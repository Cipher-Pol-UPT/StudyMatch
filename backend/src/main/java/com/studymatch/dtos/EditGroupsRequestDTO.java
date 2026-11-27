package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

// every group after the edit: a group left out is removed, an id of null is a new group
public record EditGroupsRequestDTO(@NotEmpty List<@NotNull @Valid EditedGroup> groups) {

  public record EditedGroup(Long id, @NotEmpty List<@NotNull Long> members) {
  }

}
