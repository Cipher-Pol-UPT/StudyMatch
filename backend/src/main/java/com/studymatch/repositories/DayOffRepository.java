package com.studymatch.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.DayOff;

public interface DayOffRepository extends JpaRepository<DayOff, Long> {

  List<DayOff> findByAcademicYearOrderByFirstDay(AcademicYear academicYear);

  Optional<DayOff> findByIdAndAcademicYear(Long id, AcademicYear academicYear);

  // the first one of the year sharing a day with firstDay..lastDay: it starts by lastDay and ends from firstDay
  Optional<DayOff> findFirstByAcademicYearAndFirstDayLessThanEqualAndLastDayGreaterThanEqualOrderByFirstDay(
      AcademicYear academicYear, LocalDate lastDay, LocalDate firstDay);

}
