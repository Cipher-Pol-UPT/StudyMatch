package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.CategoryRepository;

@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;

  public CategoryService(CategoryRepository categoryRepository) {
    this.categoryRepository = categoryRepository;
  }

}
