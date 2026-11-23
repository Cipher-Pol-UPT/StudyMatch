package com.studymatch.dtos;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

// the people to add to a group chat, anyone already in is skipped
public record AddMembersRequestDTO(@NotEmpty List<@NotNull Long> memberNumbers) {

}
