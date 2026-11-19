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
  private double openness;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double conscientiousness;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double extraversion;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double agreeableness;

  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double neuroticism;

  protected PersonalityProfile() {
  }

  public PersonalityProfile(double openness, double conscientiousness, double extraversion, double agreeableness,
      double neuroticism) {
    this.openness = openness;
    this.conscientiousness = conscientiousness;
    this.extraversion = extraversion;
    this.agreeableness = agreeableness;
    this.neuroticism = neuroticism;
  }

  // answers go from 1 to 5; each Big Five trait is the average of its answers, scaled to 0-1
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

    return new PersonalityProfile(traits.get(Trait.OPENNESS), traits.get(Trait.CONSCIENTIOUSNESS),
        traits.get(Trait.EXTRAVERSION), traits.get(Trait.AGREEABLENESS), traits.get(Trait.NEUROTICISM));
  }

  public Long getId() {
    return id;
  }

  public double getOpenness() {
    return openness;
  }

  public void setOpenness(double openness) {
    this.openness = openness;
  }

  public double getConscientiousness() {
    return conscientiousness;
  }

  public void setConscientiousness(double conscientiousness) {
    this.conscientiousness = conscientiousness;
  }

  public double getExtraversion() {
    return extraversion;
  }

  public void setExtraversion(double extraversion) {
    this.extraversion = extraversion;
  }

  public double getAgreeableness() {
    return agreeableness;
  }

  public void setAgreeableness(double agreeableness) {
    this.agreeableness = agreeableness;
  }

  public double getNeuroticism() {
    return neuroticism;
  }

  public void setNeuroticism(double neuroticism) {
    this.neuroticism = neuroticism;
  }

}
