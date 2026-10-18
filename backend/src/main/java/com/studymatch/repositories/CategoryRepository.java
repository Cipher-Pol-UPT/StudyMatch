package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  boolean existsByNameIgnoreCase(String name);

  List<Category> findAllByOrderByNameAsc();

}
