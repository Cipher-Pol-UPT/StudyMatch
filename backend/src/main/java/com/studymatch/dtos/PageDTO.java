package com.studymatch.dtos;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

// our own page shape, Spring's Page has no stable JSON format
public record PageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <E, T> PageDTO<T> from(Page<E> page, Function<E, T> toDTO) {
    return new PageDTO<>(page.getContent().stream().map(toDTO).toList(), page.getNumber(), page.getSize(),
        page.getTotalElements(), page.getTotalPages());
  }

}
