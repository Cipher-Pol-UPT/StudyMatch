package com.studymatch.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
public class Subject {

  public static final int MAX_WEEKLY_SESSIONS = 5;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(unique = true, updatable = false)
  private String code;

  @NotBlank
  private String name;

  @Positive
  private int ects;

  @ManyToOne
  @JoinColumn(updatable = false)
  @NotNull
  private Category category;

  @OneToMany(mappedBy = "subject", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<DegreeSubject> degreeSubjects = new HashSet<>();

  // the weekly plan, in the order Staff gave it. Empty means Generate Schedule lists it as missing
  @ElementCollection
  @CollectionTable(name = "weekly_session")
  @OrderColumn(name = "position")
  private List<WeeklySession> weeklySessions = new ArrayList<>();

  protected Subject() {
  }

  public Subject(String code, String name, int ects, Category category) {
    this.code = code;
    this.name = name;
    this.ects = ects;
    this.category = category;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getEcts() {
    return ects;
  }

  public void setEcts(int ects) {
    this.ects = ects;
  }

  public Category getCategory() {
    return category;
  }

  public Set<DegreeSubject> getDegreeSubjects() {
    return Collections.unmodifiableSet(degreeSubjects);
  }

  public void addDegree(Degree degree, int curricularYear, int semester) {
    if (degreeSubjects.stream().anyMatch(ds -> ds.getDegree().equals(degree))) {
      throw new StudyMatchException(ErrorCode.SUBJECT_ALREADY_IN_DEGREE);
    }
    degreeSubjects.add(new DegreeSubject(this, degree, curricularYear, semester));
  }

  public void removeDegree(Degree degree) {
    degreeSubjects.removeIf(ds -> ds.getDegree().equals(degree));
  }

  public List<WeeklySession> getWeeklySessions() {
    return Collections.unmodifiableList(weeklySessions);
  }

  // replaces the whole plan, checked before anything changes
  public void setWeeklySessions(List<WeeklySession> sessions) {
    if (sessions.size() > MAX_WEEKLY_SESSIONS) {
      throw new StudyMatchException(ErrorCode.TOO_MANY_WEEKLY_SESSIONS, Map.of("max", MAX_WEEKLY_SESSIONS));
    }
    weeklySessions.clear();
    weeklySessions.addAll(sessions);
  }

}
