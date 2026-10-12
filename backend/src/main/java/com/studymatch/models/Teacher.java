package com.studymatch.models;

import jakarta.persistence.Entity;

@Entity
public class Teacher extends User {

  protected Teacher() {
  }

  public Teacher(String personalEmail, String name, String password) {
    super(personalEmail, name, password);
  }

}
