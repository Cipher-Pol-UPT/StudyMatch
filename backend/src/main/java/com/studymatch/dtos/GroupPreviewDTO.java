package com.studymatch.dtos;

import java.util.List;

import com.studymatch.enums.Language;

// one generated group; sharedLanguages empty means the group has no language in common (GR4), the page marks it
public record GroupPreviewDTO(int number, int size, List<Language> sharedLanguages, List<Member> members) {

  public record Member(Long number, String name, String classSection) {
  }

}
