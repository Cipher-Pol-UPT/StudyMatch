package com.studymatch.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.CategoryLevelDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.EnrolmentResultDTO;
import com.studymatch.dtos.EnrolmentResultDTO.Result;
import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Student;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.EnrolmentRepository;

@Service
public class EnrolmentService {

  private final EnrolmentRepository enrolmentRepository;
  private final LessonService lessonService;
  private final AbsenceService absenceService;
  private final SubjectEditionService subjectEditionService;

  public EnrolmentService(EnrolmentRepository enrolmentRepository, LessonService lessonService,
      AbsenceService absenceService, SubjectEditionService subjectEditionService) {
    this.enrolmentRepository = enrolmentRepository;
    this.lessonService = lessonService;
    this.absenceService = absenceService;
    this.subjectEditionService = subjectEditionService;
  }

  // enrol students: the students come from the controller since StudentService already depends on this service
  @Transactional
  public List<EnrolmentResultDTO> enrolStudents(Long subjectEditionId, String classSectionCode,
      List<Long> studentNumbers, Map<Long, Student> students) {
    // locked so two Staff enrolling at the same time can't both enrol the same student
    SubjectEdition edition = subjectEditionService.getLockedCurrentEdition(subjectEditionId);
    ClassSection classSection = edition.getClassSection(classSectionCode.trim());

    List<EnrolmentResultDTO> results = new ArrayList<>();
    for (Long number : new LinkedHashSet<>(studentNumbers)) {
      results.add(enrol(number, students.get(number), edition, classSection));
    }
    return results;
  }

  private EnrolmentResultDTO enrol(Long number, Student student, SubjectEdition edition, ClassSection classSection) {
    if (student == null) {
      return EnrolmentResultDTO.of(number, null, Result.NOT_FOUND);
    }
    Result result = Result.ENROLLED;
    if (enrolmentRepository.existsByStudentAndSubjectEdition(student, edition)) {
      result = Result.ALREADY_ENROLLED;
    } else if (enrolmentRepository.existsByStudentAndSubjectEdition_SubjectAndStatus(student, edition.getSubject(),
        EnrolmentStatus.PASSED)) {
      result = Result.ALREADY_PASSED;
    } else {
      enrolmentRepository.save(new Enrolment(student, classSection));
    }
    return EnrolmentResultDTO.of(number, student.getName(), result);
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
    return attendanceRate(lessons, absences);
  }

  // attendance in one subject only (what the teacher sees)
  public Double getSubjectAttendanceRate(Enrolment enrolment) {
    return attendanceRate(lessonService.countLessons(enrolment.getClassSection()),
        absenceService.countUnjustifiedAbsences(enrolment));
  }

  // null before the first lesson, there's nothing to measure yet
  private static Double attendanceRate(long lessons, long absences) {
    if (lessons == 0) {
      return null;
    }
    return 100.0 * (lessons - absences) / lessons;
  }

  public List<Enrolment> getEnrolments(Student student) {
    return enrolmentRepository.findByStudent(student);
  }

  // only students of this edition can be picked, the number goes back so the message can say which one
  public Student getEnrolledStudent(Long subjectEditionId, Long studentNumber) {
    return enrolmentRepository.findBySubjectEditionIdAndStudent_Number(subjectEditionId, studentNumber)
        .map(Enrolment::getStudent)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.STUDENT_NOT_ENROLLED, Map.of("number", studentNumber)));
  }

  // view students (teacher): an edition has a few hundred students at most, so filtering here is fine
  @Transactional(readOnly = true)
  public List<EditionStudentDTO> listStudentsOfEdition(Long subjectEditionId, String search, String classSection) {
    String searchText = search == null ? "" : search.trim();
    String classCode = classSection == null ? "" : classSection.trim();
    return enrolmentRepository.findBySubjectEditionId(subjectEditionId).stream()
        .filter(enrolment -> classCode.isEmpty() || enrolment.getClassSection().getCode().equalsIgnoreCase(classCode))
        .filter(enrolment -> searchText.isEmpty() || matchesSearch(enrolment.getStudent(), searchText))
        .map(this::toEditionStudent)
        .sorted(Comparator.comparing(EditionStudentDTO::name).thenComparing(EditionStudentDTO::number))
        .toList();
  }

  // part of the name (any capitals) or the exact number
  private static boolean matchesSearch(Student student, String search) {
    return student.getName().toLowerCase().contains(search.toLowerCase())
        || student.getNumber().toString().equals(search);
  }

  private EditionStudentDTO toEditionStudent(Enrolment enrolment) {
    Student student = enrolment.getStudent();
    // students with an active statute show "attendance not applicable"
    Double attendance = student.hasActiveStatute() ? null : getSubjectAttendanceRate(enrolment);
    return new EditionStudentDTO(student.getNumber(), student.getName(), enrolment.getClassSection().getCode(),
        PersonalityProfileDTO.from(student.getPersonalityProfile()),
        getCategoryLevel(student, categoryOf(enrolment).getId()), attendance);
  }

  // finished subjects only, weighted by ECTS, so a 10 ECTS subject counts twice as much as a 5 ECTS one
  public Double getAverageGrade(Student student) {
    double weightedSum = 0;
    int totalEcts = 0;
    for (Enrolment enrolment : finishedEnrolments(student)) {
      int ects = enrolment.getSubjectEdition().getSubject().getEcts();
      weightedSum += ects * enrolment.getFinalGrade();
      totalEcts += ects;
    }
    return totalEcts == 0 ? null : weightedSum / totalEcts;
  }

  // one level per category the student has finished something in, sorted by category name
  public List<CategoryLevelDTO> getCategoryLevels(Student student) {
    Map<Long, List<Enrolment>> byCategory = finishedEnrolments(student).stream()
        .collect(Collectors.groupingBy(enrolment -> categoryOf(enrolment).getId()));

    return byCategory.values().stream()
        .map(enrolments -> new CategoryLevelDTO(categoryOf(enrolments.getFirst()).getId(),
            categoryOf(enrolments.getFirst()).getName(),
            enrolments.stream().mapToInt(Enrolment::getFinalGrade).average().orElseThrow()))
        .sorted(Comparator.comparing(CategoryLevelDTO::category))
        .toList();
  }

  // null when the student hasn't finished anything in that category yet
  public Double getCategoryLevel(Student student, Long categoryId) {
    return getCategoryLevels(student).stream()
        .filter(level -> level.categoryId().equals(categoryId))
        .map(CategoryLevelDTO::level)
        .findFirst()
        .orElse(null);
  }

  private List<Enrolment> finishedEnrolments(Student student) {
    return enrolmentRepository.findByStudent(student).stream().filter(Enrolment::isFinished).toList();
  }

  private static Category categoryOf(Enrolment enrolment) {
    return enrolment.getSubjectEdition().getSubject().getCategory();
  }

}
