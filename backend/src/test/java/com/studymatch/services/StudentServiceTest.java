package com.studymatch.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.TutorBadgeDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.Achievement;
import com.studymatch.enums.Language;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Category;
import com.studymatch.models.ClassSection;
import com.studymatch.models.Degree;
import com.studymatch.models.EarnedAchievement;
import com.studymatch.models.Enrolment;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Rating;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Subject;
import com.studymatch.models.Teacher;
import com.studymatch.models.TutorRole;
import com.studymatch.repositories.AbsenceRepository;
import com.studymatch.repositories.EarnedAchievementRepository;
import com.studymatch.repositories.EnrolmentRepository;
import com.studymatch.repositories.LessonRepository;
import com.studymatch.repositories.RatingRepository;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.repositories.TutorRoleRepository;

class StudentServiceTest {

  private static final Long MARIA = 50001L;
  private static final Long JOAO = 50002L;
  private static final Long TEACHER = 50003L;
  private static final Long STAFF = 50004L;

  private StudentRepository studentRepository;
  private EnrolmentRepository enrolmentRepository;
  private LessonRepository lessonRepository;
  private AbsenceRepository absenceRepository;
  private TutorRoleRepository tutorRoleRepository;
  private RatingRepository ratingRepository;
  private EarnedAchievementRepository earnedAchievementRepository;
  private StudentService studentService;

  private Student maria;

  @BeforeEach
  void setUp() {
    studentRepository = mock(StudentRepository.class);
    enrolmentRepository = mock(EnrolmentRepository.class);
    lessonRepository = mock(LessonRepository.class);
    absenceRepository = mock(AbsenceRepository.class);
    tutorRoleRepository = mock(TutorRoleRepository.class);
    ratingRepository = mock(RatingRepository.class);
    earnedAchievementRepository = mock(EarnedAchievementRepository.class);
    studentService = new StudentService(studentRepository, enrolmentRepository, lessonRepository, absenceRepository,
        tutorRoleRepository, ratingRepository, earnedAchievementRepository);

    maria = studentWithNumber(MARIA, "Maria Costa");
    maria.setLanguages(Set.of(Language.PORTUGUESE));
    maria.setPersonalityProfile(new PersonalityProfile(0.8, 0.6, 0.4, 0.7));
    when(studentRepository.findById(MARIA)).thenReturn(Optional.of(maria));
  }

  @Test
  @DisplayName("A student sees their whole profile")
  void studentSeesTheirWholeProfile() {
    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertEquals("Maria Costa", profile.name());
    assertNotNull(profile.privateProfile());
    assertEquals(2, profile.privateProfile().curricularYear());
    assertEquals(0.8, profile.privateProfile().personalityProfile().leadership());
  }

  @Test
  @DisplayName("Another student sees only the public part")
  void anotherStudentSeesOnlyThePublicPart() {
    StudentProfileDTO profile = studentService.getProfile(MARIA, studentWithNumber(JOAO, "Joao Silva"));

    assertEquals("Maria Costa", profile.name());
    assertEquals(Set.of(Language.PORTUGUESE), profile.languages());
    assertNull(profile.privateProfile());
  }

  @Test
  @DisplayName("Staff see the whole profile")
  void staffSeeTheWholeProfile() {
    Staff staff = new Staff("staff@example.com", "staff@test.com", "Test Staff", "hash");
    ReflectionTestUtils.setField(staff, "number", STAFF);

    assertNotNull(studentService.getProfile(MARIA, staff).privateProfile());
  }

  @Test
  @DisplayName("A teacher of the student sees the whole profile")
  void theirTeacherSeesTheWholeProfile() {
    Teacher teacher = teacherWithNumber(TEACHER);
    when(enrolmentRepository.existsByStudentAndSubjectEdition_Teachers_Number(maria, TEACHER)).thenReturn(true);

    assertNotNull(studentService.getProfile(MARIA, teacher).privateProfile());
  }

  @Test
  @DisplayName("A teacher who doesn't teach the student sees only the public part")
  void otherTeacherSeesOnlyThePublicPart() {
    assertNull(studentService.getProfile(MARIA, teacherWithNumber(TEACHER)).privateProfile());
  }

  @Test
  @DisplayName("Attendance counts justified absences as attended")
  void justifiedAbsencesCountAsAttended() {
    Enrolment enrolment = enrolmentWithLessons(20);
    when(absenceRepository.countByEnrolmentAndJustifiedFalse(enrolment)).thenReturn(5L);

    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertEquals(75.0, profile.privateProfile().attendance());
    verify(absenceRepository).countByEnrolmentAndJustifiedFalse(enrolment);
  }

  @Test
  @DisplayName("Attendance adds up the lessons of every enrolment")
  void attendanceAddsUpEveryEnrolment() {
    Enrolment first = mock(Enrolment.class);
    Enrolment second = mock(Enrolment.class);
    ClassSection firstSection = mock(ClassSection.class);
    ClassSection secondSection = mock(ClassSection.class);
    when(first.getClassSection()).thenReturn(firstSection);
    when(second.getClassSection()).thenReturn(secondSection);
    when(enrolmentRepository.findByStudent(maria)).thenReturn(List.of(first, second));
    when(lessonRepository.countByClassSection(firstSection)).thenReturn(10L);
    when(lessonRepository.countByClassSection(secondSection)).thenReturn(30L);
    when(absenceRepository.countByEnrolmentAndJustifiedFalse(second)).thenReturn(4L);

    assertEquals(90.0, studentService.getProfile(MARIA, maria).privateProfile().attendance());
  }

  @Test
  @DisplayName("A student without enrolments has no academic history or attendance")
  void noEnrolmentsMeansNoAcademicHistory() {
    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertNull(profile.privateProfile().attendance());
    assertFalse(profile.privateProfile().hasAcademicHistory());
  }

  @Test
  @DisplayName("An enrolment without lessons yet has academic history but no attendance")
  void enrolmentWithoutLessonsHasNoAttendance() {
    enrolmentWithLessons(0);

    StudentProfileDTO profile = studentService.getProfile(MARIA, maria);

    assertNull(profile.privateProfile().attendance());
    assertTrue(profile.privateProfile().hasAcademicHistory());
  }

  @Test
  @DisplayName("A student with an active special statute has no attendance (GR7)")
  void activeStatuteHasNoAttendance() {
    maria.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().plusMonths(6));
    enrolmentWithLessons(20);

    assertNull(studentService.getProfile(MARIA, maria).privateProfile().attendance());
  }

  @Test
  @DisplayName("A tutor badge shows the subject and the average rating")
  void tutorBadgeShowsTheAverageRating() {
    TutorRole tutorRole = tutorRoleFor("Programming I");
    List<Rating> ratings = List.of(ratingWithStars(5), ratingWithStars(4), ratingWithStars(3));
    when(ratingRepository.findByParticipation_StudyRoom_TutorRole(tutorRole)).thenReturn(ratings);

    TutorBadgeDTO badge = studentService.getProfile(MARIA, maria).tutorBadges().get(0);

    assertEquals("Programming I", badge.subject());
    assertEquals(4.0, badge.averageRating());
    assertEquals(3, badge.ratingCount());
  }

  @Test
  @DisplayName("A tutor badge without ratings has no average")
  void tutorBadgeWithoutRatingsHasNoAverage() {
    tutorRoleFor("Programming I");

    TutorBadgeDTO badge = studentService.getProfile(MARIA, maria).tutorBadges().get(0);

    assertNull(badge.averageRating());
    assertEquals(0, badge.ratingCount());
  }

  @Test
  @DisplayName("Achievements are shown with the date they were earned")
  void achievementsAreShown() {
    EarnedAchievement earned = new EarnedAchievement(maria, Achievement.FIRST_STUDYROOM);
    when(earnedAchievementRepository.findByStudentOrderByEarnedAtDesc(maria)).thenReturn(List.of(earned));

    StudentProfileDTO profile = studentService.getProfile(MARIA, studentWithNumber(JOAO, "Joao Silva"));

    assertEquals(Achievement.FIRST_STUDYROOM, profile.achievements().get(0).achievement());
    assertEquals(earned.getEarnedAt(), profile.achievements().get(0).earnedAt());
  }

  @Test
  @DisplayName("An unknown student number throws NOT_FOUND")
  void unknownStudentThrowsNotFound() {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> studentService.getProfile(99999L, maria));

    assertEquals(ErrorCode.NOT_FOUND, e.getCode());
  }

  @Test
  @DisplayName("Editing saves the banner, about me, skills, interests and languages")
  void editingSavesTheEditableFields() {
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO("data:image/png;base64,iVBORw0KGgo=",
        "I like databases.", Set.of("Java"), Set.of("Chess"), Set.of(Language.ENGLISH, Language.SPANISH));

    StudentProfileDTO profile = studentService.updateProfile(MARIA, changes);

    assertEquals("data:image/png;base64,iVBORw0KGgo=", profile.banner());
    assertEquals("I like databases.", profile.aboutMe());
    assertEquals(Set.of("Java"), profile.skills());
    assertEquals(Set.of("Chess"), profile.interests());
    assertEquals(Set.of(Language.ENGLISH, Language.SPANISH), profile.languages());
    assertNotNull(profile.privateProfile());
    verify(studentRepository).save(maria);
  }

  @Test
  @DisplayName("Editing with no languages throws AT_LEAST_ONE_LANGUAGE_REQUIRED")
  void editingWithoutLanguagesIsRejected() {
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO(null, null, Set.of(), Set.of(), Set.of());

    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.updateProfile(MARIA, changes));

    assertEquals(ErrorCode.AT_LEAST_ONE_LANGUAGE_REQUIRED, e.getCode());
  }

  @Test
  @DisplayName("Editing with an invalid banner throws INVALID_BANNER")
  void editingWithAnInvalidBannerIsRejected() {
    UpdateProfileRequestDTO changes = new UpdateProfileRequestDTO("data:image/gif;base64,R0lGODlh", null, Set.of(),
        Set.of(), Set.of(Language.ENGLISH));

    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> studentService.updateProfile(MARIA, changes));

    assertEquals(ErrorCode.INVALID_BANNER, e.getCode());
  }

  private Enrolment enrolmentWithLessons(long lessons) {
    Enrolment enrolment = mock(Enrolment.class);
    ClassSection classSection = mock(ClassSection.class);
    when(enrolment.getClassSection()).thenReturn(classSection);
    when(enrolmentRepository.findByStudent(maria)).thenReturn(List.of(enrolment));
    when(lessonRepository.countByClassSection(classSection)).thenReturn(lessons);
    return enrolment;
  }

  private TutorRole tutorRoleFor(String subjectName) {
    TutorRole tutorRole = mock(TutorRole.class);
    when(tutorRole.getSubject()).thenReturn(new Subject("PROG1", subjectName, 6, new Category("Programming")));
    when(tutorRoleRepository.findByStudent(maria)).thenReturn(List.of(tutorRole));
    return tutorRole;
  }

  private static Rating ratingWithStars(int stars) {
    Rating rating = mock(Rating.class);
    when(rating.getStars()).thenReturn(stars);
    return rating;
  }

  private static Student studentWithNumber(Long number, String name) {
    Student student = new Student(name.toLowerCase().replace(" ", ".") + "@example.com", name, "hash",
        new Degree("Test Degree"), 2);
    ReflectionTestUtils.setField(student, "number", number);
    return student;
  }

  private static Teacher teacherWithNumber(Long number) {
    Teacher teacher = new Teacher("teacher@example.com", "teacher@test.com", "Test Teacher", "hash");
    ReflectionTestUtils.setField(teacher, "number", number);
    return teacher;
  }

}
