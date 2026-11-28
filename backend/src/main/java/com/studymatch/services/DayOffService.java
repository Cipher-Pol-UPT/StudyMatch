package com.studymatch.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.DayOffRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.DayOff;
import com.studymatch.repositories.DayOffRepository;

// the days without classes of an academic year (#336). Lessons only exist once attendance is taken, so there is
// nothing to remove yet: publishing a schedule (#347) skips the days datesWithoutClasses gives
@Service
public class DayOffService {

  private final DayOffRepository dayOffRepository;
  private final AcademicYearService academicYearService;

  public DayOffService(DayOffRepository dayOffRepository, AcademicYearService academicYearService) {
    this.dayOffRepository = dayOffRepository;
    this.academicYearService = academicYearService;
  }

  @Transactional(readOnly = true)
  public List<DayOff> list(int startYear) {
    return dayOffRepository.findByAcademicYearOrderByFirstDay(academicYearService.getAcademicYear(startYear));
  }

  // checked in this order so several problems always give the same code: the right way round, inside one
  // semester, then not on a day already marked
  @Transactional
  public DayOff add(int startYear, DayOffRequestDTO request) {
    AcademicYear academicYear = currentYear(startYear);
    LocalDate firstDay = request.firstDay();
    LocalDate lastDay = request.lastDay() == null ? firstDay : request.lastDay();
    if (lastDay.isBefore(firstDay)) {
      throw new StudyMatchException(ErrorCode.DAY_OFF_ENDS_BEFORE_START);
    }
    // a range can't run over the break between the semesters, which has no classes anyway
    int semester = academicYear.semesterOf(firstDay);
    if (semester == 0 || academicYear.semesterOf(lastDay) != semester) {
      throw new StudyMatchException(ErrorCode.DAY_OFF_OUTSIDE_SEMESTERS);
    }
    dayOffRepository
        .findFirstByAcademicYearAndFirstDayLessThanEqualAndLastDayGreaterThanEqualOrderByFirstDay(academicYear,
            lastDay, firstDay)
        .ifPresent(existing -> {
          throw new StudyMatchException(ErrorCode.DAY_OFF_OVERLAPS, Map.of("firstDay", existing.getFirstDay().toString(),
              "lastDay", existing.getLastDay().toString(), "reason", existing.getReason()));
        });
    return dayOffRepository.save(new DayOff(academicYear, firstDay, lastDay, request.reason()));
  }

  @Transactional
  public void remove(int startYear, Long id) {
    AcademicYear academicYear = currentYear(startYear);
    DayOff dayOff = dayOffRepository.findByIdAndAcademicYear(id, academicYear)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
    dayOffRepository.delete(dayOff);
  }

  // every date of the year without classes, in order, for publishing a schedule (#347)
  @Transactional(readOnly = true)
  public Set<LocalDate> datesWithoutClasses(AcademicYear academicYear) {
    Set<LocalDate> dates = new TreeSet<>();
    for (DayOff dayOff : dayOffRepository.findByAcademicYearOrderByFirstDay(academicYear)) {
      dayOff.getFirstDay().datesUntil(dayOff.getLastDay().plusDays(1)).forEach(dates::add);
    }
    return dates;
  }

  // like the semester dates, an older year is done and only the current one changes
  private AcademicYear currentYear(int startYear) {
    AcademicYear academicYear = academicYearService.getAcademicYear(startYear);
    if (!academicYearService.isCurrent(academicYear)) {
      throw new StudyMatchException(ErrorCode.ACADEMIC_YEAR_NOT_CURRENT);
    }
    return academicYear;
  }

}
