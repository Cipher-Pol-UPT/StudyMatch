package com.studymatch.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class StudentBannerTest {

  private final Student student = new Student("maria@example.com", "Maria Costa", "secret", new Degree("Informatics"), 2);

  @Test
  void acceptsPngAndJpegBanners() {
    student.setBanner(bannerOfSize("png", 1024));
    assertThat(student.getBanner()).startsWith("data:image/png;base64,");

    student.setBanner(bannerOfSize("jpeg", 1024));
    assertThat(student.getBanner()).startsWith("data:image/jpeg;base64,");
  }

  @Test
  void removesTheBannerWhenItIsNull() {
    student.setBanner(bannerOfSize("png", 1024));
    student.setBanner(null);

    assertThat(student.getBanner()).isNull();
  }

  @Test
  void acceptsABannerOfExactlyTheMaximumSize() {
    student.setBanner(bannerOfSize("png", Student.MAX_BANNER_BYTES));

    assertThat(student.getBanner()).isNotNull();
  }

  @Test
  void rejectsABannerLargerThanTheMaximumSize() {
    assertInvalidBanner(bannerOfSize("png", Student.MAX_BANNER_BYTES + 1));
  }

  @Test
  void rejectsOtherImageFormats() {
    assertInvalidBanner(bannerOfSize("gif", 1024));
  }

  @Test
  void rejectsTextThatIsNotADataUrl() {
    assertInvalidBanner("https://example.com/banner.png");
  }

  @Test
  void rejectsInvalidBase64() {
    assertInvalidBanner("data:image/png;base64,ab=cd");
  }

  private void assertInvalidBanner(String banner) {
    assertThatThrownBy(() -> student.setBanner(banner))
        .isInstanceOfSatisfying(StudyMatchException.class,
            exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_BANNER));
  }

  private static String bannerOfSize(String format, int bytes) {
    return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(new byte[bytes]);
  }

}
