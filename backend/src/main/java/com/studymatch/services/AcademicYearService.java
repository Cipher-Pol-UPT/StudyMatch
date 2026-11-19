package com.studymatch.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.events.AcademicYearStartedEvent;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.repositories.AcademicYearRepository;

@Service
public class AcademicYearService {

  private final AcademicYearRepository academicYearRepository;
  private final ApplicationEventPublisher eventPublisher;

  public AcademicYearService(AcademicYearRepository academicYearRepository,
      ApplicationEventPublisher eventPublisher) {
    this.academicYearRepository = academicYearRepository;
    this.eventPublisher = eventPublisher;
  }

  // newest first, the year Staff usually wants is at the top
  public List<AcademicYear> listAcademicYears() {
    return academicYearRepository.findAllByOrderByStartYearDesc();
  }

  // only years already registered can be used, they aren't created on the fly
  public AcademicYear getAcademicYear(int startYear) {
    return academicYearRepository.findById(startYear)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // the newest registered year is the current one, starting a new year just adds a newer one
  public Optional<AcademicYear> findCurrent() {
    return academicYearRepository.findFirstByOrderByStartYearDesc();
  }

  public boolean isCurrent(AcademicYear year) {
    return findCurrent().map(current -> current.getStartYear() == year.getStartYear()).orElse(false);
  }

  // the year after the current one, or the one today falls in when nothing is registered yet
  public int nextStartYear() {
    return findCurrent().map(current -> current.next().getStartYear())
        .orElseGet(() -> AcademicYear.startYearOf(LocalDate.now()));
  }

  // Staff confirms the year they saw, so a second click or another Staff member can't skip a year.
  // the listeners (profile wipe) run in this same transaction, if one fails the year isn't created
  @Transactional
  public AcademicYear startNext(int confirmedStartYear) {
    if (confirmedStartYear != nextStartYear()) {
      throw new StudyMatchException(ErrorCode.ACADEMIC_YEAR_NOT_NEXT);
    }
    AcademicYear academicYear = academicYearRepository.save(new AcademicYear(confirmedStartYear));
    eventPublisher.publishEvent(new AcademicYearStartedEvent(academicYear));
    return academicYear;
  }

}
