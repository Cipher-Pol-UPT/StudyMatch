package com.studymatch.models;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Entity
public class Degree {

  public static final int MIN_YEARS = 1;
  public static final int MAX_YEARS = 6;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(unique = true)
  private String name;

  // no setter on purpose: students may already be in any of these years
  @Min(MIN_YEARS)
  @Max(MAX_YEARS)
  @ColumnDefault("3")
  private int years;

  protected Degree() {
  }

  public Degree(String name, int years) {
    this.name = name;
    this.years = years;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getYears() {
    return years;
  }

  public boolean hasYear(int curricularYear) {
    return curricularYear >= 1 && curricularYear <= years;
  }

}
