package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;
import com.studymatch.repositories.EnrolmentRepository;

@Service
public class EnrolmentService {

  private final EnrolmentRepository enrolmentRepository;
  private final LessonService lessonService;
  private final AbsenceService absenceService;

  public EnrolmentService(EnrolmentRepository enrolmentRepository, LessonService lessonService,
      AbsenceService absenceService) {
    this.enrolmentRepository = enrolmentRepository;
    this.lessonService = lessonService;
    this.absenceService = absenceService;
  }

  public boolean isTaughtBy(Student student, Long teacherNumber) {
    return enrolmentRepository.existsByStudentAndSubjectEdition_Teachers_Number(student, teacherNumber);
  }

  public boolean hasAcademicHistory(Student student) {
    return enrolmentRepository.existsByStudent(student);
  }

  public Double getAttendanceRate(Student student) {
    long lessons = 0;
    long absences = 0;
    for (Enrolment enrolment : enrolmentRepository.findByStudent(student)) {
      lessons += lessonService.countLessons(enrolment.getClassSection());
      absences += absenceService.countUnjustifiedAbsences(enrolment);
    }

    if (lessons == 0) {
      return null;
    }
    return 100.0 * (lessons - absences) / lessons;
  }

}
