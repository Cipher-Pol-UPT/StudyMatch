package com.studymatch.services;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.UnavailableSlotDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Teacher;
import com.studymatch.models.UnavailableSlot;
import com.studymatch.repositories.UnavailableSlotRepository;

@Service
public class UnavailableSlotService {

  private static final Comparator<UnavailableSlot> GRID_ORDER = Comparator.comparing(UnavailableSlot::getDay)
      .thenComparing(UnavailableSlot::getStart);

  private final UnavailableSlotRepository unavailableSlotRepository;
  private final TeacherService teacherService;
  private final AcademicYearService academicYearService;

  public UnavailableSlotService(UnavailableSlotRepository unavailableSlotRepository, TeacherService teacherService,
      AcademicYearService academicYearService) {
    this.unavailableSlotRepository = unavailableSlotRepository;
    this.teacherService = teacherService;
    this.academicYearService = academicYearService;
  }

  // the current year's slots by day and time. none marked, or no year yet, means available all week
  public List<UnavailableSlot> listCurrent(Long teacherNumber) {
    return academicYearService.findCurrent()
        .map(year -> sorted(unavailableSlotRepository.findByTeacher_NumberAndAcademicYear(teacherNumber, year)))
        .orElse(List.of());
  }

  // the teacher saves the whole grid at once. the same slot twice counts once, and every slot is checked
  // before the old ones go, so one bad slot changes nothing. slots that hit a published class go to Staff (#349)
  @Transactional
  public List<UnavailableSlot> replaceCurrent(Long teacherNumber, Collection<UnavailableSlotDTO> slots) {
    Teacher teacher = teacherService.getLockedTeacher(teacherNumber);
    AcademicYear year = academicYearService.findCurrent()
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NO_CURRENT_ACADEMIC_YEAR));
    List<UnavailableSlot> newSlots = slots.stream().distinct()
        .map(slot -> new UnavailableSlot(teacher, year, slot.day(), slot.start()))
        .toList();

    unavailableSlotRepository.deleteByTeacherAndYear(teacherNumber, year);
    return sorted(unavailableSlotRepository.saveAll(newSlots));
  }

  private static List<UnavailableSlot> sorted(List<UnavailableSlot> slots) {
    return slots.stream().sorted(GRID_ORDER).toList();
  }

}
