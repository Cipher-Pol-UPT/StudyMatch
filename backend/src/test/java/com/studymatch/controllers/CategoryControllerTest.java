package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.studymatch.dtos.CategoryRequestDTO;
import com.studymatch.dtos.CategoryResponseDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.services.CategoryService;

class CategoryControllerTest {

  private static final CategoryRequestDTO REGISTER = new CategoryRequestDTO("Mathematics");

  private CategoryService categoryService;
  private CategoryController controller;

  @BeforeEach
  void setUp() {
    categoryService = mock(CategoryService.class);
    controller = new CategoryController(categoryService);
  }

  // register category

  @Test
  @DisplayName("Registering a category is 201 with the new category's data")
  void registerCategoryIs201() {
    // Arrange - @PreAuthorize isn't active without Spring, so the role check isn't tested here
    when(categoryService.registerCategory(REGISTER)).thenReturn(new Category("Mathematics"));

    // Act
    ResponseEntity<CategoryResponseDTO> result = controller.registerCategory(REGISTER);

    // Assert
    assertEquals(201, result.getStatusCode().value());
    assertEquals("Mathematics", result.getBody().name());
  }

  @Test
  @DisplayName("When the category already exists, the service's error goes through to the handler")
  void duplicateCategoryIsPassedOn() {
    // Arrange
    when(categoryService.registerCategory(REGISTER))
        .thenThrow(new StudyMatchException(ErrorCode.CATEGORY_ALREADY_EXISTS));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.registerCategory(REGISTER));

    // Assert
    assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS, e.getCode());
  }

}
