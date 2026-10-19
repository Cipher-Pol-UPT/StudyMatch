package com.studymatch.models;

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

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(unique = true)
  private String name;

  // fixed when the degree is created
  @Min(1)
  @Max(6)
  private int curricularYears;

  protected Degree() {
  }

  public Degree(String name, int curricularYears) {
    this.name = name;
    this.curricularYears = curricularYears;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getCurricularYears() {
    return curricularYears;
  }

  public void setName(String name) {
    this.name = name;
  }

}
