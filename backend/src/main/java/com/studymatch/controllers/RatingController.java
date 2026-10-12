package com.studymatch.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.services.RatingService;

@RestController
@RequestMapping("/ratings/")
public class RatingController {

  private final RatingService ratingService;

  public RatingController(RatingService ratingService) {
    this.ratingService = ratingService;
  }

}
