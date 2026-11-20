package com.studymatch.dtos;

import java.time.LocalDateTime;

import com.studymatch.models.Rating;

// anonymous on purpose, who wrote it is never sent
public record RatingDTO(String subject, int stars, String comment, LocalDateTime ratedAt) {

  public static RatingDTO from(Rating rating) {
    return new RatingDTO(rating.getTutorRole().getSubject().getName(), rating.getStars(), rating.getComment(),
        rating.getRatedAt());
  }

}
