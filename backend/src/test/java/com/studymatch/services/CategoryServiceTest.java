package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.studymatch.dtos.CategoryRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.repositories.CategoryRepository;

class CategoryServiceTest {

  private CategoryRepository categoryRepository;
  private CategoryService categoryService;

  @BeforeEach
  void setUp() {
    categoryRepository = mock(CategoryRepository.class);
    categoryService = new CategoryService(categoryRepository);

    // save returns what it was given
    when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  // register category

  @Test
  @DisplayName("A new category is saved with its name")
  void newCategoryIsSaved() {
    // Act
    Category category = categoryService.registerCategory(new CategoryRequestDTO("Mathematics"));

    // Assert
    assertEquals("Mathematics", category.getName());
    verify(categoryRepository).save(category);
  }

  @Test
  @DisplayName("Spaces around the name are removed before checking and saving")
  void nameIsTrimmed() {
    // Act
    Category category = categoryService.registerCategory(new CategoryRequestDTO("  Mathematics  "));

    // Assert
    assertEquals("Mathematics", category.getName());
    verify(categoryRepository).existsByNameIgnoreCase("Mathematics");
  }

  @Test
  @DisplayName("A category with a name that already exists gets CATEGORY_ALREADY_EXISTS and isn't saved")
  void duplicateCategoryIsRefused() {
    // Arrange
    when(categoryRepository.existsByNameIgnoreCase("Mathematics")).thenReturn(true);

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> categoryService.registerCategory(new CategoryRequestDTO("Mathematics")));

    // Assert
    assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS, e.getCode());
    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("If someone else saves the same name first, the unique column's error becomes CATEGORY_ALREADY_EXISTS")
  void simultaneousDuplicateIsRefused() {
    // Arrange - the check passed, but by the time we save another Staff already registered it
    when(categoryRepository.save(any(Category.class))).thenThrow(new DataIntegrityViolationException("unique name"));

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> categoryService.registerCategory(new CategoryRequestDTO("Mathematics")));

    // Assert
    assertEquals(ErrorCode.CATEGORY_ALREADY_EXISTS, e.getCode());
  }

  // list categories

  @Test
  @DisplayName("Listing returns the categories in the order the repository sorted them (by name)")
  void listReturnsSortedCategories() {
    // Arrange
    List<Category> sorted = List.of(new Category("Algorithms"), new Category("Mathematics"));
    when(categoryRepository.findAllByOrderByNameAsc()).thenReturn(sorted);

    // Act
    List<Category> result = categoryService.listCategories();

    // Assert
    assertEquals(sorted, result);
  }

  // get one category

  @Test
  @DisplayName("Getting a category by id returns it")
  void getCategoryById() {
    // Arrange
    Category mathematics = new Category("Mathematics");
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(mathematics));

    // Act
    Category result = categoryService.getCategory(1L);

    // Assert
    assertEquals(mathematics, result);
  }

  @Test
  @DisplayName("An id with no category gets NOT_FOUND")
  void unknownIdIsNotFound() {
    // Arrange
    when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> categoryService.getCategory(99L));

    // Assert
    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

}
