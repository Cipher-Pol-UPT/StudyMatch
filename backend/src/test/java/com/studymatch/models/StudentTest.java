package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.Language;
import com.studymatch.enums.Role;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class StudentTest {

  private Student student;

  @BeforeEach
  void setUp() {
    student = new Student("student@example.com", "Test Student", "hash", new Degree("Test Degree", 3), 1);
  }

  // role + account state

  @Test
  @DisplayName("A student's role is STUDENT")
  void roleIsStudent() {
    assertEquals(Role.STUDENT, student.getRole());
  }

  @Test
  @DisplayName("New accounts have to change their one-time password")
  void newStudentMustChangePassword() {
    assertTrue(student.isMustChangePassword());
  }

  @Test
  @DisplayName("An active account is not blocked")
  void activeStudentIsNotBlocked() {
    assertFalse(student.isBlocked());
  }

  @Test
  @DisplayName("A blocked account is blocked")
  void blockedStudentIsBlocked() {
    // Act
    student.setAccountStatus(AccountStatus.BLOCKED);

    // Assert
    assertTrue(student.isBlocked());
  }

  // personality test gate (GR1)

  @Test
  @DisplayName("A student without a personality profile needs to take the test")
  void requiresPersonalityTestWithoutProfile() {
    assertTrue(student.requiresPersonalityTest());
  }

  @Test
  @DisplayName("A student with a personality profile doesn't need the test")
  void doesNotRequirePersonalityTestWithProfile() {
    // Arrange
    student.setPersonalityProfile(new PersonalityProfile(0.5, 0.5, 0.5, 0.5));

    // Act + Assert
    assertFalse(student.requiresPersonalityTest());
  }

  // institutional email

  @Test
  @DisplayName("Institutional email is number@students.<domain>")
  void generatesInstitutionalEmailFromNumber() {
    // Arrange - number is normally set by the db, so we set it by hand
    ReflectionTestUtils.setField(student, "number", 50003L);

    // Act
    student.generateInstitutionalEmail("studymatch.com");

    // Assert
    assertEquals("50003@students.studymatch.com", student.getInstitutionalEmail());
  }

  @Test
  @DisplayName("Can't generate the email before the student is saved")
  void cannotGenerateInstitutionalEmailBeforeSaved() {
    // no number yet -> should blow up
    assertThrows(IllegalStateException.class, () -> student.generateInstitutionalEmail("studymatch.com"));
  }

  // languages (GR4)

  @Test
  @DisplayName("Setting languages replaces the previous ones")
  void setLanguagesReplacesPreviousLanguages() {
    // Arrange
    student.setLanguages(Set.of(Language.PORTUGUESE, Language.ENGLISH));

    // Act
    student.setLanguages(Set.of(Language.SPANISH));

    // Assert
    assertEquals(Set.of(Language.SPANISH), student.getLanguages());
  }

  @Test
  @DisplayName("A student needs at least one language")
  void emptyLanguagesAreRejected() {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> student.setLanguages(Set.of()));

    // Assert
    assertEquals(ErrorCode.AT_LEAST_ONE_LANGUAGE_REQUIRED, e.getCode());
  }

  @Test
  @DisplayName("Languages can't be changed behind setLanguages' back")
  void languagesCannotBeModifiedDirectly() {
    // the getter gives a read-only view, so this has to fail
    assertThrows(UnsupportedOperationException.class, () -> student.getLanguages().add(Language.ENGLISH));
  }

  // statute (boundaries around today)

  @Test
  @DisplayName("A statute ending today is accepted and active")
  void statuteEndingTodayIsActive() {
    // Act - today is the limit, should still be allowed
    student.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now());

    // Assert
    assertTrue(student.hasActiveStatute());
  }

  @Test
  @DisplayName("A statute can't end in the past")
  void statuteEndingYesterdayIsRejected() {
    // Act - one day before the limit
    StudyMatchException e = assertThrows(StudyMatchException.class,
        () -> student.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now().minusDays(1)));

    // Assert
    assertEquals(ErrorCode.STATUTE_END_DATE_IN_PAST, e.getCode());
  }

  @Test
  @DisplayName("A statute that ended yesterday is no longer active")
  void expiredStatuteIsNotActive() {
    // Arrange - assignStatute won't take a past date, so we fake an old one
    student.assignStatute(StatuteType.WORKING_STUDENT, LocalDate.now());
    ReflectionTestUtils.setField(student, "statuteEndDate", LocalDate.now().minusDays(1));

    // Act + Assert
    assertFalse(student.hasActiveStatute());
  }

  @Test
  @DisplayName("Removing the statute clears it")
  void removeStatuteClearsIt() {
    // Arrange
    student.assignStatute(StatuteType.STUDENT_PARENT, LocalDate.now().plusMonths(6));

    // Act
    student.removeStatute();

    // Assert
    assertFalse(student.hasActiveStatute());
    assertNull(student.getStatuteType());
  }

  @Test
  @DisplayName("A PNG banner is saved")
  void pngBannerIsSaved() {
    // Arrange
    String banner = pngBanner(1024);

    // Act
    student.setBanner(banner);

    // Assert
    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A JPEG banner is saved")
  void jpegBannerIsSaved() {
    // Arrange
    String banner = jpegBanner(1024);

    // Act
    student.setBanner(banner);

    // Assert
    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A null banner removes the current one")
  void nullBannerRemovesIt() {
    // Arrange
    student.setBanner(pngBanner(1024));

    // Act
    student.setBanner(null);

    // Assert
    assertNull(student.getBanner());
  }

  @Test
  @DisplayName("A banner just under 5 MB is saved")
  void bannerUnderTheLimitIsSaved() {
    // Arrange
    String banner = pngBanner(Student.MAX_BANNER_BYTES - 1);

    // Act
    student.setBanner(banner);

    // Assert
    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A banner of exactly 5 MB is saved")
  void bannerAtTheLimitIsSaved() {
    // Arrange
    String banner = pngBanner(Student.MAX_BANNER_BYTES);

    // Act
    student.setBanner(banner);

    // Assert
    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A banner over 5 MB is rejected")
  void bannerOverTheLimitIsRejected() {
    // Arrange
    String banner = pngBanner(Student.MAX_BANNER_BYTES + 1);

    // Act + Assert
    assertInvalidBanner(banner);
  }

  @Test
  @DisplayName("Image formats other than JPG and PNG are rejected")
  void otherFormatsAreRejected() {
    // Arrange
    String banner = "data:image/gif;base64," + Base64.getEncoder().encodeToString("GIF89a".getBytes());

    // Act + Assert
    assertInvalidBanner(banner);
  }

  @Test
  @DisplayName("A file that says PNG but isn't one is rejected")
  void fakePngIsRejected() {
    // Arrange
    String banner = "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[1024]);

    // Act + Assert
    assertInvalidBanner(banner);
  }

  @Test
  @DisplayName("A JPEG sent as PNG is rejected")
  void jpegSentAsPngIsRejected() {
    // Arrange
    String banner = jpegBanner(1024).replace("image/jpeg", "image/png");

    // Act + Assert
    assertInvalidBanner(banner);
  }

  @Test
  @DisplayName("A link instead of a data URL is rejected")
  void linkIsRejected() {
    // Act + Assert
    assertInvalidBanner("https://example.com/banner.png");
  }

  @Test
  @DisplayName("Invalid base64 is rejected")
  void invalidBase64IsRejected() {
    // Act + Assert
    assertInvalidBanner("data:image/png;base64,ab=cd");
  }

  @Test
  @DisplayName("A rejected banner keeps the current one")
  void rejectedBannerKeepsTheCurrentOne() {
    // Arrange
    String banner = pngBanner(1024);
    student.setBanner(banner);

    // Act
    assertThrows(StudyMatchException.class, () -> student.setBanner(pngBanner(Student.MAX_BANNER_BYTES + 1)));

    // Assert
    assertEquals(banner, student.getBanner());
  }

  private void assertInvalidBanner(String banner) {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> student.setBanner(banner));

    assertEquals(ErrorCode.INVALID_BANNER, e.getCode());
  }

  private static String pngBanner(int bytes) {
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageOfSize(bytes, 0x89, 0x50, 0x4E, 0x47));
  }

  private static String jpegBanner(int bytes) {
    return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageOfSize(bytes, 0xFF, 0xD8, 0xFF));
  }

  private static byte[] imageOfSize(int bytes, int... signature) {
    byte[] image = new byte[bytes];
    for (int i = 0; i < signature.length; i++) {
      image[i] = (byte) signature[i];
    }
    return image;
  }

}
