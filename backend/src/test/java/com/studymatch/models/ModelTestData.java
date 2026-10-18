package com.studymatch.models;

import java.util.Set;

// shortcuts for building model objects in tests (fake values, every call returns a new object)
public final class ModelTestData {

  private ModelTestData() {
  }

  public static Degree degree() {
    return new Degree("Test Degree");
  }

  public static Student student(String name) {
    String personalEmail = name.toLowerCase().replace(" ", ".") + "@example.com";
    return new Student(personalEmail, name, "hash", degree(), 1);
  }

  public static Teacher teacher() {
    return new Teacher("teacher@example.com", "teacher@test.com", "Test Teacher", "hash");
  }

  public static Subject subject() {
    return new Subject("TEST", "Test Subject", 6, new Category("Test Category"));
  }

  public static SubjectEdition subjectEdition() {
    return new SubjectEdition(subject(), new AcademicYear(2026), Set.of(teacher()));
  }

  public static TutorRole tutorRole(Student tutor) {
    return new TutorApplication(tutor, subject(), "test motivation").approve(teacher());
  }

  public static Enrolment enrolment(Student student) {
    SubjectEdition edition = subjectEdition();
    edition.addClassSection("T1");
    return new Enrolment(student, edition.getClassSections().iterator().next());
  }

}
