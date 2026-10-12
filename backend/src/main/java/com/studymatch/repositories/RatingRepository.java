package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Rating;

public interface RatingRepository extends JpaRepository<Rating, Long> {

}
