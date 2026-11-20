package com.studymatch.services;

import java.util.List;
import java.util.OptionalDouble;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.RateTutorRequestDTO;
import com.studymatch.dtos.RatingDTO;
import com.studymatch.enums.Achievement.SubjectRating;
import com.studymatch.events.TutorActivityEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Participation;
import com.studymatch.models.Rating;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.RatingRepository;

@Service
public class RatingService {

  private final RatingRepository ratingRepository;
  private final ParticipationService participationService;
  private final ApplicationEventPublisher eventPublisher;

  public RatingService(RatingRepository ratingRepository, ParticipationService participationService,
      ApplicationEventPublisher eventPublisher) {
    this.ratingRepository = ratingRepository;
    this.participationService = participationService;
    this.eventPublisher = eventPublisher;
  }

  // the model checks the attendance was confirmed and it's within 7 days of the end
  @Transactional
  public RatingDTO rate(Long studyRoomId, Long studentNumber, RateTutorRequestDTO request) {
    Participation participation = participationService.getParticipation(studyRoomId, studentNumber);
    if (ratingRepository.existsByParticipation(participation)) {
      throw new StudyMatchException(ErrorCode.ROOM_ALREADY_RATED);
    }
    String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
    Rating rating = new Rating(participation, request.stars(), comment);
    // two ratings sent at the same time both pass the check above, the unique participation stops the second
    try {
      rating = ratingRepository.saveAndFlush(rating);
    } catch (DataIntegrityViolationException e) {
      throw new StudyMatchException(ErrorCode.ROOM_ALREADY_RATED);
    }
    eventPublisher.publishEvent(new TutorActivityEvent(rating.getTutorRole().getStudent()));
    return RatingDTO.from(rating);
  }

  // the average of each student's average, so one student rating many times counts once. null with no ratings
  public Double getAverageStars(TutorRole tutorRole) {
    return average(ratingRepository.findAverageStarsPerStudent(tutorRole));
  }

  // achievements: the same average, and how many different students it's from
  public SubjectRating getSubjectRating(TutorRole tutorRole) {
    List<Double> perStudent = ratingRepository.findAverageStarsPerStudent(tutorRole);
    return new SubjectRating(average(perStudent), perStudent.size());
  }

  private static Double average(List<Double> perStudent) {
    OptionalDouble average = perStudent.stream().mapToDouble(Double::doubleValue).average();
    return average.isPresent() ? average.getAsDouble() : null;
  }

  public long countRatings(TutorRole tutorRole) {
    return ratingRepository.countByParticipation_StudyRoom_TutorRole(tutorRole);
  }

  // public, shown on the tutor's profile, newest first
  @Transactional(readOnly = true)
  public List<RatingDTO> listRatings(Long tutorNumber) {
    return ratingRepository.findByParticipation_StudyRoom_TutorRole_Student_NumberOrderByRatedAtDesc(tutorNumber)
        .stream()
        .map(RatingDTO::from)
        .toList();
  }

}
