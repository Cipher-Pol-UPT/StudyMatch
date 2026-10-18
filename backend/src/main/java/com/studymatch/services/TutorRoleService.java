package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.models.Student;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.TutorRoleRepository;

@Service
public class TutorRoleService {

  private final TutorRoleRepository tutorRoleRepository;
  private final RatingService ratingService;

  public TutorRoleService(TutorRoleRepository tutorRoleRepository, RatingService ratingService) {
    this.tutorRoleRepository = tutorRoleRepository;
    this.ratingService = ratingService;
  }

  public List<TutorBadgeDTO> getTutorBadges(Student student) {
    return tutorRoleRepository.findByStudent(student).stream()
        .map(this::toTutorBadge)
        .toList();
  }

  private TutorBadgeDTO toTutorBadge(TutorRole tutorRole) {
    return new TutorBadgeDTO(tutorRole.getSubject().getName(), ratingService.getAverageStars(tutorRole),
        ratingService.countRatings(tutorRole));
  }

}
