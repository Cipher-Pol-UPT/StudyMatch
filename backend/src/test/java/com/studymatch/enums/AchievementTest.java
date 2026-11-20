package com.studymatch.enums;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.studymatch.enums.Achievement.SubjectRating;
import com.studymatch.enums.Achievement.TutorRecord;

class AchievementTest {

  private static TutorRecord rooms(long countedRooms) {
    return new TutorRecord(countedRooms, 0, List.of());
  }

  private static TutorRecord students(long studentsTutored) {
    return new TutorRecord(0, studentsTutored, List.of());
  }

  private static TutorRecord rated(SubjectRating... subjects) {
    return new TutorRecord(0, 0, List.of(subjects));
  }

  @Test
  @DisplayName("First StudyRoom needs 1 counted room")
  void firstStudyRoom() {
    // Act + Assert
    assertFalse(Achievement.FIRST_STUDYROOM.isMetBy(rooms(0)));
    assertTrue(Achievement.FIRST_STUDYROOM.isMetBy(rooms(1)));
  }

  @Test
  @DisplayName("Dedicated Tutor needs 10 counted rooms: 9 isn't enough, 10 and 11 are")
  void dedicatedTutor() {
    // Act + Assert
    assertFalse(Achievement.DEDICATED_TUTOR.isMetBy(rooms(9)));
    assertTrue(Achievement.DEDICATED_TUTOR.isMetBy(rooms(10)));
    assertTrue(Achievement.DEDICATED_TUTOR.isMetBy(rooms(11)));
  }

  @Test
  @DisplayName("Mentor needs 15 different students: 14 isn't enough, 15 and 16 are")
  void mentor() {
    // Act + Assert
    assertFalse(Achievement.MENTOR.isMetBy(students(14)));
    assertTrue(Achievement.MENTOR.isMetBy(students(15)));
    assertTrue(Achievement.MENTOR.isMetBy(students(16)));
  }

  @Test
  @DisplayName("Top Rated needs 4.5 or more: 4.49 isn't enough, 4.5 and 4.51 are, with 5 students")
  void topRatedAverage() {
    // Act + Assert
    assertFalse(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(4.49, 5))));
    assertTrue(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(4.5, 5))));
    assertTrue(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(4.51, 5))));
  }

  @Test
  @DisplayName("Top Rated needs at least 5 different students: 4 isn't enough, 5 and 6 are")
  void topRatedStudents() {
    // Act + Assert
    assertFalse(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(5.0, 4))));
    assertTrue(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(5.0, 5))));
    assertTrue(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(5.0, 6))));
  }

  @Test
  @DisplayName("Top Rated is per subject: one subject meeting it is enough, two halves don't add up")
  void topRatedIsPerSubject() {
    // Act + Assert
    assertTrue(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(3.0, 9), new SubjectRating(4.8, 5))));
    assertFalse(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(5.0, 3), new SubjectRating(5.0, 3))));
  }

  @Test
  @DisplayName("A subject nobody rated doesn't count for Top Rated")
  void topRatedWithoutRatings() {
    // Act + Assert
    assertFalse(Achievement.TOP_RATED.isMetBy(rated(new SubjectRating(null, 0))));
    assertFalse(Achievement.TOP_RATED.isMetBy(rated()));
  }

}
