package com.studymatch.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.studymatch.dtos.CategoryRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.repositories.CategoryRepository;

@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;

  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

  // "Mathematics" and " mathematics " are the same category
  public Category registerCategory(CategoryRequestDTO request) {
    String name = request.name().trim();
    if (categoryRepository.existsByNameIgnoreCase(name)) {
      throw new StudyMatchException(ErrorCode.CATEGORY_ALREADY_EXISTS);
    }

    // two Staff registering the same name at once both pass the check above, the unique column stops the second
    try {
      return categoryRepository.save(new Category(name));
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.CATEGORY_ALREADY_EXISTS);
    }
  }

}
