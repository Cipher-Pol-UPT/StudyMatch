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
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.models.Category;
import com.studymatch.services.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/categories/")
@Tag(name = "Categories", description = "Subject categories like Programming, students get a level in each")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  // any logged-in user: Staff pick one for a subject, everyone else sees them on subjects and profiles
  @GetMapping
  @Operation(summary = "Every category")
  public ResponseEntity<List<CategoryResponseDTO>> listCategories() {
    return ResponseEntity.ok(categoryService.listCategories().stream().map(CategoryResponseDTO::from).toList());
  }

  @GetMapping("{id}")
  @Operation(summary = "One category")
  @ErrorCodes(ErrorCode.NOT_FOUND)
  public ResponseEntity<CategoryResponseDTO> getCategory(@PathVariable Long id) {
    return ResponseEntity.ok(CategoryResponseDTO.from(categoryService.getCategory(id)));
  }

  @PostMapping
  @PreAuthorize("hasRole('STAFF')")
  @Operation(summary = "Register a category")
  @ErrorCodes(ErrorCode.CATEGORY_ALREADY_EXISTS)
  public ResponseEntity<CategoryResponseDTO> registerCategory(@Valid @RequestBody CategoryRequestDTO registerRequest) {
    Category category = categoryService.registerCategory(registerRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponseDTO.from(category));
  }

}
