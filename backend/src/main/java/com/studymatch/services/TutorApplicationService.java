package com.studymatch.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.TutorApplicationStatus;
import com.studymatch.models.TutorApplication;
import com.studymatch.repositories.TutorApplicationRepository;

@Service
public class TutorApplicationService {

  private final TutorApplicationRepository tutorApplicationRepository;

  public TutorApplicationService(TutorApplicationRepository tutorApplicationRepository) {
    this.tutorApplicationRepository = tutorApplicationRepository;
  }

  public int countPending(Long studentNumber) {
    return findPending(studentNumber).size();
  }

  // block account: nobody can decide them anymore
  @Transactional
  public int cancelPending(Long studentNumber) {
    List<TutorApplication> applications = findPending(studentNumber);
    applications.forEach(TutorApplication::cancel);
    tutorApplicationRepository.saveAll(applications);
    return applications.size();
  }

  private List<TutorApplication> findPending(Long studentNumber) {
    return tutorApplicationRepository.findByStudentNumberAndStatus(studentNumber, TutorApplicationStatus.PENDING);
  }

}
