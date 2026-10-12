package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.RatingRepository;

@Service
public class RatingService {

  private final RatingRepository ratingRepository;

  public RatingService(RatingRepository ratingRepository) {
    this.ratingRepository = ratingRepository;
  }

}
