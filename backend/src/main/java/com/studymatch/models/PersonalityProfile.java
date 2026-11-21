package com.studymatch.models;

import java.util.Arrays;
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

  public static final int QUESTION_COUNT = 20;

  private enum Trait {
    OPENNESS, CONSCIENTIOUSNESS, EXTRAVERSION, AGREEABLENESS, NEUROTICISM
  }

  // the five traits in OCEAN order, as analytics lists them
  public static final List<String> TRAITS = Arrays.stream(Trait.values()).map(Enum::name).toList();

  private record Item(Trait trait, boolean reversed) {
  }

  // the Mini-IPIP (Donnellan et al., 2006) in its own order, four items per trait, all weighted the same
  private static final List<Item> ITEMS = List.of(
      new Item(Trait.EXTRAVERSION, false), // am the life of the party
      new Item(Trait.AGREEABLENESS, false), // sympathize with others' feelings
      new Item(Trait.CONSCIENTIOUSNESS, false), // get chores done right away
      new Item(Trait.NEUROTICISM, false), // have frequent mood swings
      new Item(Trait.OPENNESS, false), // have a vivid imagination
      new Item(Trait.EXTRAVERSION, true), // don't talk a lot
      new Item(Trait.AGREEABLENESS, true), // am not interested in other people's problems
      new Item(Trait.CONSCIENTIOUSNESS, true), // often forget to put things back in their proper place
      new Item(Trait.NEUROTICISM, true), // am relaxed most of the time
      new Item(Trait.OPENNESS, true), // am not interested in abstract ideas
      new Item(Trait.EXTRAVERSION, false), // talk to a lot of different people at parties
      new Item(Trait.AGREEABLENESS, false), // feel others' emotions
      new Item(Trait.CONSCIENTIOUSNESS, false), // like order
      new Item(Trait.NEUROTICISM, false), // get upset easily
      new Item(Trait.OPENNESS, true), // have difficulty understanding abstract ideas
      new Item(Trait.EXTRAVERSION, true), // keep in the background
      new Item(Trait.AGREEABLENESS, true), // am not really interested in others
      new Item(Trait.CONSCIENTIOUSNESS, true), // make a mess of things
      new Item(Trait.NEUROTICISM, true), // seldom feel blue
      new Item(Trait.OPENNESS, true)); // do not have a good imagination

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

  public double getConscientiousness() {
    return conscientiousness;
  }

  public double getExtraversion() {
    return extraversion;
  }

  public double getAgreeableness() {
    return agreeableness;
  }

  public double getNeuroticism() {
    return neuroticism;
  }

  // analytics: the highest of the five, a tie goes to the first in OCEAN order
  public String strongestTrait() {
    double[] scores = { openness, conscientiousness, extraversion, agreeableness, neuroticism };
    int strongest = 0;
    for (int i = 1; i < scores.length; i++) {
      if (scores[i] > scores[strongest]) {
        strongest = i;
      }
    }
    return Trait.values()[strongest].name();
  }

}
