package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.studymatch.models.Rating;
import com.studymatch.models.TutorRole;

public interface RatingRepository extends JpaRepository<Rating, Long> {

  @Query("SELECT AVG(r.stars) FROM Rating r WHERE r.participation.studyRoom.tutorRole = :tutorRole")
  Double findAverageStarsByTutorRole(@Param("tutorRole") TutorRole tutorRole);

  long countByParticipation_StudyRoom_TutorRole(TutorRole tutorRole);

}
