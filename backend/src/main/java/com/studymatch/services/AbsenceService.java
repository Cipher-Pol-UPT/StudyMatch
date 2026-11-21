package com.studymatch.services;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.studymatch.models.Absence;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;
import com.studymatch.repositories.AbsenceRepository;

@Service
public class AbsenceService {

  private final AbsenceRepository absenceRepository;

  public AbsenceService(AbsenceRepository absenceRepository) {
    this.absenceRepository = absenceRepository;
  }

  // only the current class, lessons are counted from it too, so after a class change attendance can't go below 0
  // analytics: the unjustified absences of these enrolments in one query
  public List<Absence> listUnjustifiedAbsences(Collection<Enrolment> enrolments) {
    return absenceRepository.findByEnrolmentInAndJustifiedFalse(enrolments);
  }

  public long countUnjustifiedAbsences(Enrolment enrolment) {
    return absenceRepository.countByEnrolmentAndJustifiedFalseAndLesson_ClassSection(enrolment,
        enrolment.getClassSection());
  }

  public List<Absence> listAbsences(Lesson lesson) {
    return absenceRepository.findByLesson(lesson);
  }

  // justified by enrolment, a missing one was present; only this class is touched, someone who moved keeps theirs
  public void registerAbsences(Lesson lesson, List<Enrolment> enrolments, Map<Enrolment, Boolean> absent) {
    Map<Enrolment, Absence> existing = listAbsences(lesson).stream()
        .collect(Collectors.toMap(Absence::getEnrolment, absence -> absence));
    for (Enrolment enrolment : enrolments) {
      Absence absence = existing.get(enrolment);
      Boolean justified = absent.get(enrolment);
      if (justified == null) {
        if (absence != null) {
          absenceRepository.delete(absence);
        }
      } else if (absence == null) {
        absenceRepository.save(new Absence(lesson, enrolment, justified));
      } else {
        absence.setJustified(justified);
      }
    }
  }

  // every class's absences, a student who moved class still had one in this lesson
  public void deleteAbsences(Lesson lesson) {
    absenceRepository.deleteByLesson(lesson);
  }

}
