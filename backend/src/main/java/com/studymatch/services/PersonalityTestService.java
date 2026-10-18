package com.studymatch.services;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.studymatch.models.PersonalityProfile;

@Service
public class PersonalityTestService {

  public static final int QUESTION_COUNT = 15;

  private enum Trait {
    OPENNESS, CONSCIENTIOUSNESS, EXTRAVERSION, AGREEABLENESS, NEUROTICISM
  }

  private record Item(Trait trait, boolean reversed) {
  }

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

  public PersonalityProfile score(List<Integer> answers) {
    if (answers.size() != QUESTION_COUNT) {
      throw new IllegalArgumentException("Expected " + QUESTION_COUNT + " answers but got " + answers.size());
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

}
