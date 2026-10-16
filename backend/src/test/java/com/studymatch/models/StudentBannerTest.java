package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class StudentBannerTest {

  private Student student;

  @BeforeEach
  void setUp() {
    student = new Student("student@example.com", "Test Student", "hash", new Degree("Test Degree"), 1);
  }

  @Test
  @DisplayName("A PNG banner is saved")
  void pngBannerIsSaved() {
    String banner = bannerOfSize("png", 1024);

    student.setBanner(banner);

    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A JPEG banner is saved")
  void jpegBannerIsSaved() {
    String banner = bannerOfSize("jpeg", 1024);

    student.setBanner(banner);

    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A null banner removes the current one")
  void nullBannerRemovesIt() {
    student.setBanner(bannerOfSize("png", 1024));

    student.setBanner(null);

    assertNull(student.getBanner());
  }

  @Test
  @DisplayName("A banner just under 5 MB is saved")
  void bannerUnderTheLimitIsSaved() {
    String banner = bannerOfSize("png", Student.MAX_BANNER_BYTES - 1);

    student.setBanner(banner);

    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A banner of exactly 5 MB is saved")
  void bannerAtTheLimitIsSaved() {
    String banner = bannerOfSize("png", Student.MAX_BANNER_BYTES);

    student.setBanner(banner);

    assertEquals(banner, student.getBanner());
  }

  @Test
  @DisplayName("A banner over 5 MB is rejected")
  void bannerOverTheLimitIsRejected() {
    assertInvalidBanner(bannerOfSize("png", Student.MAX_BANNER_BYTES + 1));
  }

  @Test
  @DisplayName("Image formats other than JPG and PNG are rejected")
  void otherFormatsAreRejected() {
    assertInvalidBanner(bannerOfSize("gif", 1024));
  }

  @Test
  @DisplayName("A link instead of a data URL is rejected")
  void linkIsRejected() {
    assertInvalidBanner("https://example.com/banner.png");
  }

  @Test
  @DisplayName("Invalid base64 is rejected")
  void invalidBase64IsRejected() {
    assertInvalidBanner("data:image/png;base64,ab=cd");
  }

  @Test
  @DisplayName("A rejected banner keeps the current one")
  void rejectedBannerKeepsTheCurrentOne() {
    String banner = bannerOfSize("png", 1024);
    student.setBanner(banner);

    assertThrows(StudyMatchException.class, () -> student.setBanner(bannerOfSize("gif", 1024)));

    assertEquals(banner, student.getBanner());
  }

  private void assertInvalidBanner(String banner) {
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> student.setBanner(banner));

    assertEquals(ErrorCode.INVALID_BANNER, e.getCode());
  }

  private static String bannerOfSize(String format, int bytes) {
    return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(new byte[bytes]);
  }

}
