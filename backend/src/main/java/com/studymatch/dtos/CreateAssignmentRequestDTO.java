package com.studymatch.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.studymatch.enums.AssignmentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// weight comes as a whole percentage (30 = 30%). No grouping means individual work; the size order and
// "only projects can be group work" are checked by the models
public record CreateAssignmentRequestDTO(
    @NotBlank @Size(max = 100) String name,
    @NotNull AssignmentType type,
    @NotNull LocalDate date,
    @NotNull @Min(1) @Max(100) Integer weight,
    @Valid Grouping grouping) {

  public BigDecimal weightFraction() {
    return BigDecimal.valueOf(weight, 2);
  }

  public record Grouping(
      @NotNull Integer minSize,
      @NotNull Integer optimalSize,
      @NotNull Integer maxSize,
      boolean sameClass,
      List<@NotNull @Valid Separation> separations) {

    public List<Separation> separationsOrEmpty() {
      return separations == null ? List.of() : separations;
    }

  }

  // two students (by number) who can't end up in the same group
  public record Separation(@NotNull Long firstStudent, @NotNull Long secondStudent) {
  }

}
