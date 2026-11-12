package com.studymatch.dtos;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.studymatch.enums.AssignmentType;
import com.studymatch.models.Assignment;
import com.studymatch.models.GroupingRule;

// weight goes back as a whole percentage, grouping is null for individual work
public record AssignmentResponseDTO(Long id, String name, AssignmentType type, LocalDate date, int weight,
    Grouping grouping) {

  public static AssignmentResponseDTO from(Assignment assignment) {
    GroupingRule rule = assignment.getGroupingRule();
    return new AssignmentResponseDTO(assignment.getId(), assignment.getName(), assignment.getType(),
        assignment.getDate(), assignment.getWeight().movePointRight(2).intValue(),
        rule == null ? null : Grouping.from(rule));
  }

  public record Grouping(int minSize, int optimalSize, int maxSize, boolean sameClass,
      List<Separation> separations) {

    // kept in a set, so sorted to always answer in the same order
    static Grouping from(GroupingRule rule) {
      List<Separation> separations = rule.getSeparations().stream()
          .map(s -> new Separation(s.getFirstStudent().getNumber(), s.getSecondStudent().getNumber()))
          .sorted(Comparator.comparing(Separation::firstStudent).thenComparing(Separation::secondStudent))
          .toList();
      return new Grouping(rule.getMinSize(), rule.getOptimalSize(), rule.getMaxSize(), rule.isSameClass(),
          separations);
    }

  }

  public record Separation(Long firstStudent, Long secondStudent) {
  }

}
