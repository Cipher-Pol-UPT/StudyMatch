package com.studymatch.models;

import jakarta.persistence.Entity;

@Entity
public class Staff extends User {

  protected Staff() {
  }

  public Staff(String personalEmail, String name, String password) {
    super(personalEmail, name, password);
  }

}
