package com.studymatch.dtos;

// apply for tutoring (#41): the student's rank in the edition they took a subject in
public record TutorRankingDTO(int position, int editionSize, boolean eligible) {
}
