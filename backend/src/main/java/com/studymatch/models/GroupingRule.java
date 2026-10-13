package com.studymatch.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class GroupingRule {

  public static final int MIN_GROUP_SIZE = 2;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private int minSize;

  private int optimalSize;

  private int maxSize;

  private boolean sameClass;

  @OneToMany(mappedBy = "groupingRule", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Separation> separations = new HashSet<>();

  protected GroupingRule() {
  }

  public GroupingRule(int minSize, int optimalSize, int maxSize, boolean sameClass) {
    updateSizes(minSize, optimalSize, maxSize);
    this.sameClass = sameClass;
  }

  public Long getId() {
    return id;
  }

  public int getMinSize() {
    return minSize;
  }

  public int getOptimalSize() {
    return optimalSize;
  }

  public int getMaxSize() {
    return maxSize;
  }

  public void updateSizes(int minSize, int optimalSize, int maxSize) {
    if (minSize < MIN_GROUP_SIZE || minSize > optimalSize || optimalSize > maxSize) {
      throw new StudyMatchException(ErrorCode.INVALID_GROUP_SIZES);
    }
    this.minSize = minSize;
    this.optimalSize = optimalSize;
    this.maxSize = maxSize;
  }

  public boolean isSameClass() {
    return sameClass;
  }

  public void setSameClass(boolean sameClass) {
    this.sameClass = sameClass;
  }

  public Set<Separation> getSeparations() {
    return Collections.unmodifiableSet(separations);
  }

  public void addSeparation(Student firstStudent, Student secondStudent) {
    if (firstStudent.equals(secondStudent)) {
      throw new StudyMatchException(ErrorCode.SEPARATION_SAME_STUDENT);
    }
    if (separations.stream().anyMatch(s -> s.involves(firstStudent, secondStudent))) {
      throw new StudyMatchException(ErrorCode.SEPARATION_ALREADY_EXISTS);
    }
    separations.add(new Separation(this, firstStudent, secondStudent));
  }

}
