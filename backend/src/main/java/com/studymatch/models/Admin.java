package com.studymatch.models;

import jakarta.persistence.Entity;

@Entity
public class Admin extends User {

  protected Admin() {
  }

  public Admin(String personalEmail, String name, String password) {
    super(personalEmail, name, password);
  }

}
