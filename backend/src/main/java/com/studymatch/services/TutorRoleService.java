package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
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

  // a student who doesn't tutor the subject can't act as its tutor
  public TutorRole getByStudentAndSubject(Long studentNumber, Long subjectId) {
    return tutorRoleRepository.findByStudentNumberAndSubjectId(studentNumber, subjectId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.ACCESS_DENIED));
  }

  private TutorBadgeDTO toTutorBadge(TutorRole tutorRole) {
    return new TutorBadgeDTO(tutorRole.getSubject().getName(), ratingService.getAverageStars(tutorRole),
        ratingService.countRatings(tutorRole));
  }

}
