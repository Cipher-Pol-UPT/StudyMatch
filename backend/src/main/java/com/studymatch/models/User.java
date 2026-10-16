package com.studymatch.models;

import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class User {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
  @SequenceGenerator(name = "user_seq", sequenceName = "user_sequence", initialValue = 50000, allocationSize = 1)
  @Column(name = "number", nullable = false)
  private Long number;

  @NotBlank
  @Email
  @Column(unique = true)
  private String personalEmail;

  @Email
  @Column(unique = true)
  private String institutionalEmail;

  @NotBlank
  private String name;

  @NotBlank
  private String password;

  private boolean mustChangePassword = true;

  @Enumerated(EnumType.STRING)
  @NotNull
  private AccountStatus accountStatus;

  protected User() {
  }

  public User(String personalEmail, String name, String password) {
    this(personalEmail, name, password, AccountStatus.ACTIVE);
  }

  public User(String personalEmail, String name, String password, AccountStatus accountStatus) {
    this.personalEmail = personalEmail;
    this.name = name;
    this.password = password;
    this.accountStatus = accountStatus;
  }

  public Long getNumber() {
    return number;
  }

  public String getPersonalEmail() {
    return personalEmail;
  }

  public void setPersonalEmail(String personalEmail) {
    this.personalEmail = personalEmail;
  }

  public String getInstitutionalEmail() {
    return institutionalEmail;
  }

  protected void setInstitutionalEmail(String institutionalEmail) {
    this.institutionalEmail = institutionalEmail;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isMustChangePassword() {
    return mustChangePassword;
  }

  public void setMustChangePassword(boolean mustChangePassword) {
    this.mustChangePassword = mustChangePassword;
  }

  public AccountStatus getAccountStatus() {
    return accountStatus;
  }

  public void setAccountStatus(AccountStatus accountStatus) {
    this.accountStatus = accountStatus;
  }

  public boolean isBlocked() {
    return accountStatus == AccountStatus.BLOCKED;
  }

  public boolean requiresPersonalityTest() {
    return false;
  }

  public abstract Role getRole();

}
