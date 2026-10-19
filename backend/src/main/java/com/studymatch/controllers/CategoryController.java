package com.studymatch.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  // any logged-in user: Staff pick one for a subject, everyone else sees them on subjects and profiles
  @GetMapping
  public ResponseEntity<List<CategoryResponseDTO>> listCategories() {
    return ResponseEntity.ok(categoryService.listCategories().stream().map(CategoryResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  public ResponseEntity<CategoryResponseDTO> getCategory(@PathVariable Long id) {
    return ResponseEntity.ok(CategoryResponseDTO.from(categoryService.getCategory(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  public ResponseEntity<CategoryResponseDTO> registerCategory(@Valid @RequestBody CategoryRequestDTO registerRequest) {
    Category category = categoryService.registerCategory(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponseDTO.from(category));
  }

}
