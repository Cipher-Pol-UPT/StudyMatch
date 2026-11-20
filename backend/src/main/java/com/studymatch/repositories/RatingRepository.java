package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.studymatch.models.Participation;
import com.studymatch.models.Rating;
import com.studymatch.models.TutorRole;

public interface RatingRepository extends JpaRepository<Rating, Long> {

  // one average per student who rated, so rating many times doesn't weigh more
  @Query("""
      SELECT AVG(r.stars) FROM Rating r
      WHERE r.participation.studyRoom.tutorRole = :tutorRole
      GROUP BY r.participation.student""")
  List<Double> findAverageStarsPerStudent(@Param("tutorRole") TutorRole tutorRole);

  long countByParticipation_StudyRoom_TutorRole(TutorRole tutorRole);

  boolean existsByParticipation(Participation participation);

  List<Rating> findByParticipation_StudyRoom_TutorRole_Student_NumberOrderByRatedAtDesc(Long tutorNumber);

}
