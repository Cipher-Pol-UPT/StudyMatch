package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

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

  // list categories

  @Test
  @DisplayName("Listing categories is 200 with each category's data, in the service's order")
  void listCategoriesIs200() {
    // Arrange
    when(categoryService.listCategories()).thenReturn(List.of(new Category("Algorithms"), new Category("Mathematics")));

    // Act
    ResponseEntity<List<CategoryResponseDTO>> result = controller.listCategories();

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(List.of("Algorithms", "Mathematics"), result.getBody().stream().map(CategoryResponseDTO::name).toList());
  }

  @Test
  @DisplayName("With no categories it's 200 with an empty list")
  void noCategoriesIsEmptyList() {
    // Arrange
    when(categoryService.listCategories()).thenReturn(List.of());

    // Act
    ResponseEntity<List<CategoryResponseDTO>> result = controller.listCategories();

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals(List.of(), result.getBody());
  }

  // get one category

  @Test
  @DisplayName("Getting a category is 200 with its data")
  void getCategoryIs200() {
    // Arrange
    when(categoryService.getCategory(1L)).thenReturn(new Category("Mathematics"));

    // Act
    ResponseEntity<CategoryResponseDTO> result = controller.getCategory(1L);

    // Assert
    assertEquals(200, result.getStatusCode().value());
    assertEquals("Mathematics", result.getBody().name());
  }

  @Test
  @DisplayName("When the category doesn't exist, the service's error goes through to the handler")
  void unknownCategoryIsPassedOn() {
    // Arrange
    when(categoryService.getCategory(99L)).thenThrow(new StudyMatchException(ErrorCode.NOT_FOUND));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> controller.getCategory(99L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
