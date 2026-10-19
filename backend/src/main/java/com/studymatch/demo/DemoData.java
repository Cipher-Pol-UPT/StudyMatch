package com.studymatch.demo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.enums.Achievement;
import com.studymatch.enums.Language;
import com.studymatch.models.Absence;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Admin;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Degree;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Lesson;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.SubjectEdition;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.AcademicYearRepository;
import com.studymatch.repositories.CategoryRepository;
import com.studymatch.repositories.DegreeRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.SubjectEditionRepository;
import com.studymatch.repositories.SubjectRepository;
import com.studymatch.repositories.UserRepository;

@Component
@Profile("demo")
public class DemoData implements ApplicationRunner {

  public static final String PASSWORD = "StudyMatch1";

  private static final int LESSONS_PER_SUBJECT = 8;

  private final UserRepository userRepository;
  private final DegreeRepository degreeRepository;
  private final CategoryRepository categoryRepository;
  private final SubjectRepository subjectRepository;
  private final AcademicYearRepository academicYearRepository;
  private final SubjectEditionRepository subjectEditionRepository;
  private final LessonRepository lessonRepository;
  private final EnrolmentRepository enrolmentRepository;
  private final AbsenceRepository absenceRepository;
  private final EarnedAchievementRepository earnedAchievementRepository;
  private final PasswordEncoder passwordEncoder;
  private final String institutionDomain;

  public DemoData(UserRepository userRepository, DegreeRepository degreeRepository, CategoryRepository categoryRepository,
      SubjectRepository subjectRepository, AcademicYearRepository academicYearRepository,
      SubjectEditionRepository subjectEditionRepository, LessonRepository lessonRepository,
      EnrolmentRepository enrolmentRepository, AbsenceRepository absenceRepository,
      EarnedAchievementRepository earnedAchievementRepository, PasswordEncoder passwordEncoder,
      @Value("${studymatch.institution-domain}") String institutionDomain) {
    this.userRepository = userRepository;
    this.degreeRepository = degreeRepository;
    this.categoryRepository = categoryRepository;
    this.subjectRepository = subjectRepository;
    this.academicYearRepository = academicYearRepository;
    this.subjectEditionRepository = subjectEditionRepository;
    this.lessonRepository = lessonRepository;
    this.enrolmentRepository = enrolmentRepository;
    this.absenceRepository = absenceRepository;
    this.earnedAchievementRepository = earnedAchievementRepository;
    this.passwordEncoder = passwordEncoder;
    this.institutionDomain = institutionDomain;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.count() > 0) {
      return;
    }
    String hash = passwordEncoder.encode(PASSWORD);

    Degree informatics = degreeRepository.save(new Degree("Engenharia Informática", 3));
    degreeRepository.save(new Degree("Gestão de Empresas", 3));
    degreeRepository.save(new Degree("Design de Comunicação", 3));

    Category programming = categoryRepository.save(new Category("Programming"));
    Category projectManagement = categoryRepository.save(new Category("Project Management"));
    categoryRepository.save(new Category("Mathematics"));

    Subject programmingOne = subject("PROG1", "Programação I", programming, informatics, 1);
    Subject databases = subject("BD", "Bases de Dados", programming, informatics, 2);
    Subject projects = subject("GP", "Gestão de Projetos", projectManagement, informatics, 2);

    Student withHistory = student("maria.costa@example.com", "Maria Costa", hash, informatics, 2, false);
    withHistory.setPersonalityProfile(new PersonalityProfile(0.64, 0.63, 0.94, 0.43));
    withHistory.setAboutMe("Second-year student who likes building interfaces and explaining things to others.");
    withHistory.setSkills(Set.of("Java", "React", "SQL"));
    withHistory.setInterests(Set.of("Design", "Games"));
    withHistory.setLanguages(Set.of(Language.PORTUGUESE, Language.ENGLISH));
    student("tiago.santos@example.com", "Tiago Santos", hash, informatics, 1, true);

    employee(new Admin("admin@example.com", "admin@" + institutionDomain, "StudyMatch Admin", hash));
    employee(new Staff("ana.lima@example.com", "ana.lima@" + institutionDomain, "Ana Lima", hash));
    Teacher teacher = (Teacher) employee(new Teacher("joao.silva@example.com", "joao.silva@" + institutionDomain, "João Silva", hash));

    AcademicYear year = academicYearRepository.save(new AcademicYear(2026));
    enrol(withHistory, edition(programmingOne, year, teacher), false);
    enrol(withHistory, edition(databases, year, teacher), true);
    edition(projects, year, teacher);
    earnedAchievementRepository.save(new EarnedAchievement(withHistory, Achievement.FIRST_STUDYROOM));
  }

  private Subject subject(String code, String name, Category category, Degree degree, int curricularYear) {
    Subject subject = new Subject(code, name, 6, category);
    subject.addDegree(degree, curricularYear, 1);
    return subjectRepository.save(subject);
  }

  private Student student(String personalEmail, String name, String hash, Degree degree, int curricularYear,
      boolean mustChangePassword) {
    Student student = userRepository.save(new Student(personalEmail, name, hash, degree, curricularYear));
    student.generateInstitutionalEmail(institutionDomain);
    student.setMustChangePassword(mustChangePassword);
    return userRepository.save(student);
  }

  private User employee(User user) {
    user.setMustChangePassword(false);
    return userRepository.save(user);
  }

  private ClassSection edition(Subject subject, AcademicYear year, Teacher teacher) {
    SubjectEdition edition = new SubjectEdition(subject, year, Set.of(teacher));
    edition.addClassSection("T1");
    return subjectEditionRepository.save(edition).getClassSections().iterator().next();
  }

  private void enrol(Student student, ClassSection classSection, boolean justified) {
    Enrolment enrolment = enrolmentRepository.save(new Enrolment(student, classSection));
    LocalDateTime firstLesson = LocalDateTime.of(2026, 9, 21, 9, 0);
    List<Lesson> lessons = new ArrayList<>();
    for (int week = 0; week < LESSONS_PER_SUBJECT; week++) {
      lessons.add(lessonRepository.save(new Lesson(classSection, firstLesson.plusDays(3L * week))));
    }
    absenceRepository.save(new Absence(lessons.get(2), enrolment, justified));
  }

}
