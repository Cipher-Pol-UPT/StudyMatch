package com.studymatch.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.TeacherRepository;
import com.studymatch.services.SubjectEditionService;

// one Staff removing a teacher while another gives them a class. real commits, so it cleans up after itself
@SpringBootTest
@ActiveProfiles("test")
class SectionTeacherConcurrencyIntegrationTest {

  // far in the future so it's the current year while the test runs
  private static final int CURRENT_YEAR = 2094;

  // long enough for the other thread to reach the lock and be seen waiting
  private static final long WAITING_MILLIS = 700;

  @Autowired
  private SubjectEditionService subjectEditionService;

  @Autowired
  private SubjectEditionRepository subjectEditionRepository;

  @Autowired
  private SubjectRepository subjectRepository;

  @Autowired
  private TeacherRepository teacherRepository;

  @Autowired
  private CategoryRepository categoryRepository;

  @Autowired
  private AcademicYearRepository academicYearRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  private final ExecutorService otherThread = Executors.newSingleThreadExecutor();
  private TransactionTemplate transaction;

  private Category category;
  private AcademicYear year;
  private Teacher ana;
  private Teacher rui;
  private Subject subject;
  private SubjectEdition edition;

  // ana and rui teach the edition, T1 has no teacher yet
  @BeforeEach
  void setUp() {
    transaction = new TransactionTemplate(transactionManager);
    category = categoryRepository.save(new Category("IT Concurrent Section Category"));
    year = academicYearRepository.save(new AcademicYear(CURRENT_YEAR));
    ana = teacherRepository.save(new Teacher("it.concurrent.ana@example.com", "it.concurrent.ana@studymatch.com",
        "IT Concurrent Ana", "hash"));
    rui = teacherRepository.save(new Teacher("it.concurrent.rui@example.com", "it.concurrent.rui@studymatch.com",
        "IT Concurrent Rui", "hash"));
    subject = subjectRepository.save(new Subject("ITCSEC", "IT Concurrent Section Subject", 6, category));
    SubjectEdition toSave = new SubjectEdition(subject, year, Set.of(ana, rui));
    toSave.addClassSection("T1");
    edition = subjectEditionRepository.save(toSave);
  }

  @AfterEach
  void cleanUp() {
    otherThread.shutdownNow();
    subjectEditionRepository.deleteById(edition.getId());
    subjectRepository.delete(subject);
    teacherRepository.deleteAll(List.of(ana, rui));
    academicYearRepository.delete(year);
    categoryRepository.delete(category);
  }

  private Runnable removeAna() {
    return () -> subjectEditionService.updateCurrentTeachers(subject, List.of(rui.getNumber()));
  }

  private Runnable anaTeachesT1() {
    return () -> subjectEditionService.assignSectionTeacher(edition.getId(), "T1", ana.getNumber());
  }

  // the first is done but not committed when the second starts, which has to wait for it
  private Future<?> during(Runnable first, Runnable second) {
    Future<?>[] other = new Future<?>[1];
    transaction.executeWithoutResult(status -> {
      first.run();
      other[0] = otherThread.submit(second);
      assertThrows(TimeoutException.class, () -> other[0].get(WAITING_MILLIS, TimeUnit.MILLISECONDS));
    });
    return other[0];
  }

  private static StudyMatchException refusal(Future<?> second) {
    ExecutionException e = assertThrows(ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS));
    return assertInstanceOf(StudyMatchException.class, e.getCause());
  }

  // what was committed, read in its own transaction so the lazy collections load
  private Set<String> teacherNames() {
    return transaction.execute(status -> subjectEditionRepository.findById(edition.getId()).orElseThrow()
        .getTeachers().stream().map(Teacher::getName).collect(Collectors.toSet()));
  }

  private Long t1TeacherNumber() {
    return transaction.execute(status -> {
      ClassSection t1 = subjectEditionRepository.findById(edition.getId()).orElseThrow().getClassSection("T1");
      return t1.getTeacher() == null ? null : t1.getTeacher().getNumber();
    });
  }

  @Test
  @DisplayName("Ana removed while another Staff gives her T1: the second waits and is TEACHER_NOT_IN_SUBJECT_EDITION")
  void removedThenAssigned() {
    // Act
    Future<?> second = during(removeAna(), anaTeachesT1());

    // Assert
    assertSame(ErrorCode.TEACHER_NOT_IN_SUBJECT_EDITION, refusal(second).getCode());
    assertEquals(Set.of("IT Concurrent Rui"), teacherNames());
    assertNull(t1TeacherNumber());
  }

  @Test
  @DisplayName("Ana given T1 while another Staff removes her: the second waits and is TEACHER_STILL_TEACHES_SECTION")
  void assignedThenRemoved() {
    // Act
    Future<?> second = during(anaTeachesT1(), removeAna());

    // Assert
    StudyMatchException refused = refusal(second);
    assertEquals(ErrorCode.TEACHER_STILL_TEACHES_SECTION, refused.getCode());
    assertEquals(Map.of("classSections", "T1"), refused.getParams());
    assertEquals(Set.of("IT Concurrent Ana", "IT Concurrent Rui"), teacherNames());
    assertEquals(ana.getNumber(), t1TeacherNumber());
  }

}
