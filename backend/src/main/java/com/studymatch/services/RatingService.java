package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.models.TutorRole;
import com.studymatch.repositories.RatingRepository;

@Service
public class RatingService {

  private final RatingRepository ratingRepository;

  public RatingService(RatingRepository ratingRepository) {
    this.ratingRepository = ratingRepository;
  }

  public Double getAverageStars(TutorRole tutorRole) {
    return ratingRepository.findAverageStarsByTutorRole(tutorRole);
  }

  public long countRatings(TutorRole tutorRole) {
    return ratingRepository.countByParticipation_StudyRoom_TutorRole(tutorRole);
  }

}
