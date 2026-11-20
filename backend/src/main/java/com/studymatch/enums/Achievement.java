package com.studymatch.enums;

import java.util.List;

// each achievement knows its own rule, the service only gathers the tutor's numbers
public enum Achievement {

  FIRST_STUDYROOM {
    @Override
    public boolean isMetBy(TutorRecord record) {
      return record.countedRooms() >= 1;
    }
  },
  DEDICATED_TUTOR {
    @Override
    public boolean isMetBy(TutorRecord record) {
      return record.countedRooms() >= 10;
    }
  },
  TOP_RATED {
    @Override
    public boolean isMetBy(TutorRecord record) {
      return record.subjects().stream()
          .anyMatch(subject -> subject.average() != null && subject.average() >= 4.5 && subject.raters() >= 5);
    }
  },
  MENTOR {
    @Override
    public boolean isMetBy(TutorRecord record) {
      return record.studentsTutored() >= 15;
    }
  };

  // only rooms with at least this many confirmed attendees count, for the rooms and the students tutored
  public static final int MIN_ATTENDEES = 2;

  public abstract boolean isMetBy(TutorRecord record);

  // what the rules look at: counted rooms, different students who attended them, and each subject's rating
  public record TutorRecord(long countedRooms, long studentsTutored, List<SubjectRating> subjects) {
  }

  // the average of each student's average (null with no ratings) and how many students rated
  public record SubjectRating(Double average, long raters) {
  }

}
