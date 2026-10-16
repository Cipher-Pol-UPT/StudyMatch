package com.studymatch.models;

import com.studymatch.enums.Role;

import jakarta.persistence.Entity;

@Entity
public class Teacher extends User {

  protected Teacher() {
  }

  public Teacher(String personalEmail, String institutionalEmail, String name, String password) {
    super(personalEmail, name, password);
    setInstitutionalEmail(institutionalEmail);
  }

  @Override
  public Role getRole() {
    return Role.TEACHER;
  }

}
