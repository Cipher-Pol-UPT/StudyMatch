package com.studymatch.models;

import com.studymatch.enums.Role;

import jakarta.persistence.Entity;

@Entity
public class Staff extends User {

  protected Staff() {
  }

  public Staff(String personalEmail, String institutionalEmail, String name, String password) {
    super(personalEmail, name, password);
    setInstitutionalEmail(institutionalEmail);
  }

  @Override
  public Role getRole() {
    return Role.STAFF;
  }

}
