package com.studymatch.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AttendanceRequestDTO;
import com.studymatch.dtos.AttendanceSheetDTO;
import com.studymatch.dtos.CategoryLevelDTO;
import com.studymatch.dtos.EditionStudentDTO;
import com.studymatch.dtos.EnrolmentRecordDTO;
import com.studymatch.dtos.EnrolmentResultDTO;
import com.studymatch.dtos.EnrolmentResultDTO.Result;
import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.dtos.UnfinishedEnrolmentDTO;
import com.studymatch.enums.EnrolmentStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.utils.EnrolmentCsvReader;

@Service
public class EnrolmentService {

  private final EnrolmentRepository enrolmentRepository;
  private final LessonService lessonService;
  private final AbsenceService absenceService;
  private final SubjectEditionService subjectEditionService;
  private final AcademicYearService academicYearService;

  public EnrolmentService(EnrolmentRepository enrolmentRepository, LessonService lessonService,
      AbsenceService absenceService, SubjectEditionService subjectEditionService,
      AcademicYearService academicYearService) {
    this.enrolmentRepository = enrolmentRepository;
    this.lessonService = lessonService;
    this.absenceService = absenceService;
    this.subjectEditionService = subjectEditionService;
    this.academicYearService = academicYearService;
  }

  // enrol students: the students come from the controller since StudentService already depends on this service
  @Transactional
  public List<EnrolmentResultDTO> enrolStudents(Long subjectEditionId, String classSectionCode,
      List<Long> studentNumbers, Map<Long, Student> students) {
    return enrolAll(subjectEditionId, classSectionCode, studentNumbers, students);
  }

  // enrol students (CSV): same as above, each result just gets its spreadsheet row
  @Transactional
  public List<EnrolmentResultDTO> importEnrolments(Long subjectEditionId, String classSectionCode,
      List<EnrolmentCsvReader.Row> rows, Map<Long, Student> students) {
    List<EnrolmentResultDTO> results = enrolAll(subjectEditionId, classSectionCode,
        rows.stream().map(EnrolmentCsvReader.Row::number).toList(), students);
    return IntStream.range(0, rows.size())
        .mapToObj(i -> results.get(i).atRow(rows.get(i).row()))
        .toList();
  }

  // one result per number in the order sent, so both endpoints treat bad and repeated numbers the same
  private List<EnrolmentResultDTO> enrolAll(Long subjectEditionId, String classSectionCode, List<Long> numbers,
      Map<Long, Student> students) {
    // locked so two Staff enrolling at the same time can't both enrol the same student
    SubjectEdition edition = subjectEditionService.getLockedCurrentEdition(subjectEditionId);
    ClassSection classSection = edition.getClassSection(classSectionCode.trim());

    Set<Long> seen = new HashSet<>();
    List<EnrolmentResultDTO> results = new ArrayList<>();
    for (Long number : numbers) {
      if (number == null || number <= 0) {
        results.add(EnrolmentResultDTO.of(number, null, Result.INVALID_NUMBER));
      } else if (!seen.add(number)) {
        results.add(EnrolmentResultDTO.of(number, null, Result.DUPLICATE));
      } else {
        results.add(enrol(number, students.get(number), edition, classSection));
      }
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

  // change class: the same enrolment moves, so its grades and absences stay with the student
  @Transactional
  public EnrolmentRecordDTO changeClass(Long subjectEditionId, Long studentNumber, String classSectionCode) {
    // locked so a lesson of either class can't be registered halfway through the move
    SubjectEdition edition = subjectEditionService.getLockedCurrentEdition(subjectEditionId);
    ClassSection classSection = edition.getClassSection(classSectionCode.trim());
    Enrolment enrolment = getEnrolment(subjectEditionId, studentNumber);
    enrolment.changeClassSection(classSection);
    return EnrolmentRecordDTO.from(enrolment);
  }

  public boolean isTaughtBy(Student student, Long teacherNumber) {
    return enrolmentRepository.existsByStudentAndSubjectEdition_Teachers_Number(student, teacherNumber);
  }

  // any enrolment counts, even one still in progress
  public boolean hasAcademicHistory(Student student) {
    return enrolmentRepository.existsByStudent(student);
  }

  // join StudyRoom (#45): the student is enrolled in the subject's newest (current) edition
  public boolean isEnrolledInCurrentEdition(Long studentNumber, Subject subject) {
    return academicYearService.findCurrent()
        .map(year -> enrolmentRepository
            .existsByStudent_NumberAndSubjectEdition_SubjectAndSubjectEdition_AcademicYear(studentNumber, subject, year))
        .orElse(false);
  }

  public boolean hasPassed(Long studentNumber, Subject subject) {
    return enrolmentRepository.existsByStudent_NumberAndSubjectEdition_SubjectAndStatus(studentNumber, subject,
        EnrolmentStatus.PASSED);
  }

  public Double getAttendanceRate(Student student) {
    return attendanceRate(enrolmentRepository.findByStudent(student));
  }

  // what generate groups knows about a student, null where there's nothing yet
  public record GroupingEvidence(Double level, Double attendance) {
  }

  // the category first, everything when the category has nothing yet (GR6); no attendance with a statute (GR7)
  public GroupingEvidence getGroupingEvidence(Student student, Long categoryId) {
    List<Enrolment> enrolments = enrolmentRepository.findByStudent(student);
    List<Enrolment> inCategory = enrolments.stream()
        .filter(enrolment -> categoryOf(enrolment).getId().equals(categoryId)).toList();
    OptionalDouble categoryLevel = inCategory.stream().filter(Enrolment::isFinished)
        .mapToInt(Enrolment::getFinalGrade).average();
    Double level = categoryLevel.isPresent() ? Double.valueOf(categoryLevel.getAsDouble()) : averageGrade(enrolments);
    Double attendance = null;
    if (!student.hasActiveStatute()) {
      attendance = attendanceRate(inCategory);
      attendance = attendance != null ? attendance : attendanceRate(enrolments);
    }
    return new GroupingEvidence(level, attendance);
  }

  private Double attendanceRate(List<Enrolment> enrolments) {
    long lessons = 0;
    long absences = 0;
    for (Enrolment enrolment : enrolments) {
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

  // students with an active statute show "attendance not applicable" (GR7)
  private Double attendanceUnlessStatute(Enrolment enrolment) {
    return enrolment.getStudent().hasActiveStatute() ? null : getSubjectAttendanceRate(enrolment);
  }

  // register attendance: the sheet Staff fills in, with the existing record if the lesson was registered before
  @Transactional(readOnly = true)
  public AttendanceSheetDTO getAttendanceSheet(Long subjectEditionId, String classSectionCode,
      LocalDateTime startsAt) {
    ClassSection classSection = subjectEditionService.getEdition(subjectEditionId)
        .getClassSection(classSectionCode.trim());
    LocalDateTime start = lessonService.lessonStart(startsAt);
    return attendanceSheet(classSection, start, lessonService.findLesson(classSection, start));
  }

  // register attendance: the first record and a correction are the same call
  @Transactional
  public AttendanceSheetDTO registerAttendance(Long subjectEditionId, AttendanceRequestDTO request) {
    // locked so two Staff saving the same lesson at once can't both create it
    ClassSection classSection = subjectEditionService.getLockedCurrentEdition(subjectEditionId)
        .getClassSection(request.classSection().trim());
    LocalDateTime start = lessonService.lessonStart(request.startsAt());
    List<Enrolment> enrolments = classEnrolments(classSection);

    Map<Long, Enrolment> byNumber = enrolments.stream()
        .collect(Collectors.toMap(enrolment -> enrolment.getStudent().getNumber(), enrolment -> enrolment));
    // a number sent twice keeps its last justified value
    Map<Enrolment, Boolean> absent = new HashMap<>();
    for (AttendanceRequestDTO.AbsentStudent student : request.absences()) {
      Enrolment enrolment = byNumber.get(student.studentNumber());
      if (enrolment == null) {
        throw new StudyMatchException(ErrorCode.STUDENT_NOT_ENROLLED, Map.of("number", student.studentNumber()));
      }
      absent.put(enrolment, student.justified());
    }

    Lesson lesson = lessonService.getOrCreateLesson(classSection, start);
    absenceService.registerAbsences(lesson, enrolments, absent);
    return attendanceSheet(classSection, start, Optional.of(lesson));
  }

  // a lesson saved with the wrong time or class would count as held for everyone, so it can be removed
  @Transactional
  public void deleteLesson(Long subjectEditionId, String classSectionCode, LocalDateTime startsAt) {
    ClassSection classSection = subjectEditionService.getLockedCurrentEdition(subjectEditionId)
        .getClassSection(classSectionCode.trim());
    Lesson lesson = lessonService.getLesson(classSection, lessonService.lessonStart(startsAt));
    absenceService.deleteAbsences(lesson);
    lessonService.deleteLesson(lesson);
  }

  private AttendanceSheetDTO attendanceSheet(ClassSection classSection, LocalDateTime startsAt,
      Optional<Lesson> lesson) {
    Map<Enrolment, Absence> absences = lesson.map(absenceService::listAbsences).orElse(List.of()).stream()
        .collect(Collectors.toMap(Absence::getEnrolment, absence -> absence));
    List<AttendanceSheetDTO.Row> rows = classEnrolments(classSection).stream()
        .map(enrolment -> AttendanceSheetDTO.Row.from(enrolment, absences.get(enrolment),
            attendanceUnlessStatute(enrolment)))
        .toList();
    return new AttendanceSheetDTO(classSection.getCode(), startsAt, lesson.isPresent(), rows);
  }

  // by name like the other lists
  private List<Enrolment> classEnrolments(ClassSection classSection) {
    return enrolmentRepository.findByClassSection(classSection).stream()
        .sorted(Comparator.comparing((Enrolment enrolment) -> enrolment.getStudent().getName())
            .thenComparing(enrolment -> enrolment.getStudent().getNumber()))
        .toList();
  }

  public List<Enrolment> getEnrolments(Student student) {
    return enrolmentRepository.findByStudent(student);
  }

  // only students of this edition can be picked, the number goes back so the message can say which one
  public Student getEnrolledStudent(Long subjectEditionId, Long studentNumber) {
    return getEnrolment(subjectEditionId, studentNumber).getStudent();
  }

  public Enrolment getEnrolment(Long subjectEditionId, Long studentNumber) {
    return enrolmentRepository.findBySubjectEditionIdAndStudent_Number(subjectEditionId, studentNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.STUDENT_NOT_ENROLLED, Map.of("number", studentNumber)));
  }

  // grade sheet rows, by name like the student list
  public List<Enrolment> listEnrolments(Long subjectEditionId) {
    return enrolmentRepository.findBySubjectEditionId(subjectEditionId).stream()
        .sorted(Comparator.comparing((Enrolment enrolment) -> enrolment.getStudent().getName())
            .thenComparing(enrolment -> enrolment.getStudent().getNumber()))
        .toList();
  }

  // the warning before starting a new year, grouped by subject and class so Staff knows who to chase
  @Transactional(readOnly = true)
  public List<UnfinishedEnrolmentDTO> listUnfinished(AcademicYear academicYear) {
    return enrolmentRepository.findBySubjectEdition_AcademicYearAndFinalGradeIsNull(academicYear).stream()
        .map(UnfinishedEnrolmentDTO::from)
        .sorted(Comparator.comparing(UnfinishedEnrolmentDTO::subjectCode)
            .thenComparing(UnfinishedEnrolmentDTO::classSection)
            .thenComparing(UnfinishedEnrolmentDTO::studentName)
            .thenComparing(UnfinishedEnrolmentDTO::studentNumber))
        .toList();
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
    return new EditionStudentDTO(student.getNumber(), student.getName(), enrolment.getClassSection().getCode(),
        PersonalityProfileDTO.from(student.getPersonalityProfile()),
        getCategoryLevel(student, categoryOf(enrolment).getId()), attendanceUnlessStatute(enrolment));
  }

  // finished subjects only, weighted by ECTS, so a 10 ECTS subject counts twice as much as a 5 ECTS one
  public Double getAverageGrade(Student student) {
    return averageGrade(enrolmentRepository.findByStudent(student));
  }

  private static Double averageGrade(List<Enrolment> enrolments) {
    double weightedSum = 0;
    int totalEcts = 0;
    for (Enrolment enrolment : enrolments.stream().filter(Enrolment::isFinished).toList()) {
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
