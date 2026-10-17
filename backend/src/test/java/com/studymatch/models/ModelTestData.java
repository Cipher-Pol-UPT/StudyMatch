package com.studymatch.models;

import java.util.Set;

// shortcuts for building model objects in tests (fake values, every call returns a new object)
final class ModelTestData {

  private ModelTestData() {
  }

  static Degree degree() {
    return new Degree("Test Degree");
  }

  static Student student(String name) {
    String personalEmail = name.toLowerCase().replace(" ", ".") + "@example.com";
    return new Student(personalEmail, name, "hash", degree(), 1);
  }

  static Teacher teacher() {
    return new Teacher("teacher@example.com", "teacher@test.com", "Test Teacher", "hash");
  }

  static Subject subject() {
    return new Subject("TEST", "Test Subject", 6, new Category("Test Category"));
  }

  static SubjectEdition subjectEdition() {
    return new SubjectEdition(subject(), new AcademicYear(2026), Set.of(teacher()));
  }

  static TutorRole tutorRole(Student tutor) {
    return new TutorApplication(tutor, subject(), "test motivation").approve(teacher());
  }

}
