package com.studymatch.dtos;

// the average of the student's final grades in the finished subjects of this category
public record CategoryLevelDTO(Long categoryId, String category, double level) {
}
