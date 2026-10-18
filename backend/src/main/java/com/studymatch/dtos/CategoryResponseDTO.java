package com.studymatch.dtos;

import com.studymatch.models.Category;

public record CategoryResponseDTO(Long id, String name) {

  public static CategoryResponseDTO from(Category category) {
    return new CategoryResponseDTO(category.getId(), category.getName());
  }

}
