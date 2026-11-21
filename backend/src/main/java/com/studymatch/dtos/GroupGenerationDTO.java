package com.studymatch.dtos;

// what generating will do: groups is 0 when the class can't be split, searchMillis is how long the algorithm searches
public record GroupGenerationDTO(int students, int groups, int optimalSize, long searchMillis) {
}
