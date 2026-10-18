package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.CategoryRequestDTO;
import com.studymatch.dtos.CategoryResponseDTO;
import com.studymatch.models.Category;
import com.studymatch.services.CategoryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/categories/")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @PostMapping("register")
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<CategoryResponseDTO> registerCategory(@Valid @RequestBody CategoryRequestDTO registerRequest) {
    Category category = categoryService.registerCategory(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponseDTO.from(category));
  }

}
