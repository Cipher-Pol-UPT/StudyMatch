package com.studymatch.dtos;

import java.util.List;

// an assignment's groups; until the teacher confirms a regeneration, the drafts and the published groups both exist
public record AssignmentGroupsDTO(List<GroupDTO> draft, List<GroupDTO> published) {
}
