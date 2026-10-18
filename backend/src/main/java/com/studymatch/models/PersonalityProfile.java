package com.studymatch.models;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Entity
public class PersonalityProfile {

  public static final int QUESTION_COUNT = 15;

  private enum Trait {
    OPENNESS, CONSCIENTIOUSNESS, EXTRAVERSION, AGREEABLENESS, NEUROTICISM
  }

  private record Item(Trait trait, boolean reversed) {
  }

  // the trait each question measures, in the order the questions are asked
  private static final List<Item> ITEMS = List.of(
      new Item(Trait.EXTRAVERSION, false),
      new Item(Trait.AGREEABLENESS, false),
      new Item(Trait.CONSCIENTIOUSNESS, false),
      new Item(Trait.NEUROTICISM, false),
      new Item(Trait.OPENNESS, false),
      new Item(Trait.EXTRAVERSION, true),
      new Item(Trait.AGREEABLENESS, true),
      new Item(Trait.CONSCIENTIOUSNESS, true),
      new Item(Trait.NEUROTICISM, true),
      new Item(Trait.OPENNESS, true),
      new Item(Trait.EXTRAVERSION, false),
      new Item(Trait.AGREEABLENESS, false),
      new Item(Trait.CONSCIENTIOUSNESS, false),
      new Item(Trait.NEUROTICISM, false),
      new Item(Trait.OPENNESS, false));

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double leadership;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double problemSolving;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double creativity;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double execution;

  protected PersonalityProfile() {
  }

  public PersonalityProfile(double leadership, double problemSolving, double creativity, double execution) {
    this.leadership = leadership;
    this.problemSolving = problemSolving;
    this.creativity = creativity;
    this.execution = execution;
  }

  // answers go from 1 to 5; each Big Five trait is averaged and scaled to 0-1, then mixed into the four profile scores
  public static PersonalityProfile fromAnswers(List<Integer> answers) {
    if (answers.size() != QUESTION_COUNT) {
      throw new StudyMatchException(ErrorCode.VALIDATION_FAILED);
    }

    Map<Trait, Double> sums = new EnumMap<>(Trait.class);
    Map<Trait, Integer> counts = new EnumMap<>(Trait.class);
    for (int i = 0; i < QUESTION_COUNT; i++) {
      Item item = ITEMS.get(i);
      int answer = answers.get(i);
      sums.merge(item.trait(), (double) (item.reversed() ? 6 - answer : answer), Double::sum);
      counts.merge(item.trait(), 1, Integer::sum);
    }

    Map<Trait, Double> traits = new EnumMap<>(Trait.class);
    for (Trait trait : Trait.values()) {
      double mean = sums.get(trait) / counts.get(trait);
      traits.put(trait, (mean - 1) / 4);
    }

    double o = traits.get(Trait.OPENNESS);
    double c = traits.get(Trait.CONSCIENTIOUSNESS);
    double e = traits.get(Trait.EXTRAVERSION);
    double a = traits.get(Trait.AGREEABLENESS);
    double s = 1 - traits.get(Trait.NEUROTICISM);

    double leadership = 0.50 * e + 0.20 * c + 0.15 * s + 0.15 * o;
    double problemSolving = 0.45 * o + 0.30 * c + 0.25 * s;
    double creativity = 0.75 * o + 0.25 * e;
    double execution = 0.60 * c + 0.20 * s + 0.20 * a;

    return new PersonalityProfile(leadership, problemSolving, creativity, execution);
  }

  public Long getId() {
    return id;
  }

  public double getLeadership() {
    return leadership;
  }

  public void setLeadership(double leadership) {
    this.leadership = leadership;
  }

  public double getProblemSolving() {
    return problemSolving;
  }

  public void setProblemSolving(double problemSolving) {
    this.problemSolving = problemSolving;
  }

  public double getCreativity() {
    return creativity;
  }

  public void setCreativity(double creativity) {
    this.creativity = creativity;
  }

  public double getExecution() {
    return execution;
  }

  public void setExecution(double execution) {
    this.execution = execution;
  }

}
