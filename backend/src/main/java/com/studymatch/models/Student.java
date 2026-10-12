package com.studymatch.models;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.studymatch.enums.Language;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class Student extends User {

  @ManyToOne
  @NotNull
  private Degree degree;

  @Min(1)
  private int curricularYear;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  private PersonalityProfile personalityProfile;

  private String banner;

  @Size(max = 500)
  private String aboutMe;

  @ElementCollection
  private Set<@NotBlank @Size(max = 50) String> skills = new HashSet<>();

  @ElementCollection
  private Set<@NotBlank @Size(max = 50) String> interests = new HashSet<>();

  private String pfp;

  @ElementCollection
  @Enumerated(EnumType.STRING)
  private Set<Language> languages = new HashSet<>();

  @Enumerated(EnumType.STRING)
  private StatuteType statuteType;

  private LocalDate statuteEndDate;

  protected Student() {
  }

  public Student(String personalEmail, String name, String password, Degree degree, int curricularYear) {
    super(personalEmail, name, password);
    this.degree = degree;
    this.curricularYear = curricularYear;
  }

  public Degree getDegree() {
    return degree;
  }

  public void setDegree(Degree degree) {
    this.degree = degree;
  }

  public int getCurricularYear() {
    return curricularYear;
  }

  public void setCurricularYear(int curricularYear) {
    this.curricularYear = curricularYear;
  }

  public PersonalityProfile getPersonalityProfile() {
    return personalityProfile;
  }

  public void setPersonalityProfile(PersonalityProfile personalityProfile) {
    this.personalityProfile = personalityProfile;
  }

  public String getBanner() {
    return banner;
  }

  public void setBanner(String banner) {
    this.banner = banner;
  }

  public String getAboutMe() {
    return aboutMe;
  }

  public void setAboutMe(String aboutMe) {
    this.aboutMe = aboutMe;
  }

  public Set<String> getSkills() {
    return Collections.unmodifiableSet(skills);
  }

  public void setSkills(Set<String> skills) {
    this.skills.clear();
    this.skills.addAll(skills);
  }

  public Set<String> getInterests() {
    return Collections.unmodifiableSet(interests);
  }

  public void setInterests(Set<String> interests) {
    this.interests.clear();
    this.interests.addAll(interests);
  }

  public String getPfp() {
    return pfp;
  }

  public void setPfp(String pfp) {
    this.pfp = pfp;
  }

  public Set<Language> getLanguages() {
    return Collections.unmodifiableSet(languages);
  }

  public void setLanguages(Set<Language> languages) {
    if (languages.isEmpty()) {
      throw new StudyMatchException(ErrorCode.AT_LEAST_ONE_LANGUAGE_REQUIRED);
    }
    this.languages.clear();
    this.languages.addAll(languages);
  }

  public StatuteType getStatuteType() {
    return statuteType;
  }

  public LocalDate getStatuteEndDate() {
    return statuteEndDate;
  }

  public boolean hasActiveStatute() {
    return statuteType != null && !statuteEndDate.isBefore(LocalDate.now());
  }

  public void assignStatute(StatuteType statuteType, LocalDate statuteEndDate) {
    if (statuteEndDate.isBefore(LocalDate.now())) {
      throw new StudyMatchException(ErrorCode.STATUTE_END_DATE_IN_PAST);
    }
    this.statuteType = statuteType;
    this.statuteEndDate = statuteEndDate;
  }

  public void removeStatute() {
    this.statuteType = null;
    this.statuteEndDate = null;
  }

}
