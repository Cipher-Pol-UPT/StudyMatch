package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Rating;
import com.studymatch.models.TutorRole;

public interface RatingRepository extends JpaRepository<Rating, Long> {

  List<Rating> findByParticipation_StudyRoom_TutorRole(TutorRole tutorRole);

}
