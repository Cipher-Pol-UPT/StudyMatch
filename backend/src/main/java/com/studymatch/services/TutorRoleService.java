package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.dtos.TutorSubjectDTO;
import com.studymatch.enums.Achievement.SubjectRating;
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

  // create StudyRoom: the subjects a tutor can pick, empty for students who don't tutor anything
  public List<TutorSubjectDTO> getTutoredSubjects(Long studentNumber) {
    return tutorRoleRepository.findByStudentNumberOrderBySubjectNameAsc(studentNumber).stream()
        .map(TutorSubjectDTO::from)
        .toList();
  }

  // a student who doesn't tutor the subject can't act as its tutor
  public TutorRole getByStudentAndSubject(Long studentNumber, Long subjectId) {
    return tutorRoleRepository.findByStudentNumberAndSubjectId(studentNumber, subjectId)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.ACCESS_DENIED));
  }

  // apply for tutoring (#41): whether the student already tutors the subject
  public boolean hasTutorRole(Long studentNumber, Long subjectId) {
    return tutorRoleRepository.findByStudentNumberAndSubjectId(studentNumber, subjectId).isPresent();
  }

  // achievements: the rating of each subject the student tutors
  public List<SubjectRating> getSubjectRatings(Student student) {
    return tutorRoleRepository.findByStudent(student).stream()
        .map(ratingService::getSubjectRating)
        .toList();
  }

  private TutorBadgeDTO toTutorBadge(TutorRole tutorRole) {
    return new TutorBadgeDTO(tutorRole.getSubject().getName(), ratingService.getAverageStars(tutorRole),
        ratingService.countRatings(tutorRole));
  }

}
