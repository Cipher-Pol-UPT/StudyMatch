package com.studymatch.dtos;

import java.util.List;

// import groups preview: imported is how many students kept their group from the file, leftover how many were
// grouped with the others, placed the leftover students who went into a group that needed members
public record GroupImportDTO(int imported, List<Problem> skipped, int leftover, List<Long> placed,
    List<ImportedGroup> groups) {

  // a row that was skipped, or one that blocks the whole import (GROUP_IMPORT_INVALID_ROWS)
  public record Problem(int row, Long number, Reason reason) {
  }

  public enum Reason {
    // blocks the import
    INVALID,
    UNKNOWN_STUDENT,
    IN_TWO_GROUPS,
    // only skipped
    NOT_ENROLLED,
    BLOCKED,
  }

  public record ImportedGroup(Origin origin, GroupDTO group) {
  }

  public enum Origin {
    IMPORTED,
    LEFTOVER,
  }

}
