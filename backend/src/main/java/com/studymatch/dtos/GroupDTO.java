package com.studymatch.dtos;

import java.util.List;

import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;

// one group of an assignment; sharedLanguages empty means the group has no language in common (GR4), the page marks it
public record GroupDTO(Long id, int number, GroupStatus status, int size, List<Language> sharedLanguages,
    List<Member> members) {

  // attendanceApplicable is false with an active statute, whose type is never sent (GR7); blocked members stay (GR5)
  public record Member(Long number, String name, String classSection, boolean attendanceApplicable,
      boolean blocked) {
  }

}
