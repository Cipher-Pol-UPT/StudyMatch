package com.studymatch.models;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.studymatch.enums.Language;
import com.studymatch.enums.Role;
import com.studymatch.enums.StatuteType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

  public static final int MAX_BANNER_BYTES = 5 * 1024 * 1024;

  public static final int MAX_PFP_BYTES = 5 * 1024 * 1024;

  private static final Pattern IMAGE_DATA_URL = Pattern.compile("^data:image/(png|jpeg);base64,([A-Za-z0-9+/=]+)$");

  private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 0x50, 0x4E, 0x47 };

  private static final byte[] JPEG_SIGNATURE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

  @ManyToOne
  @NotNull
  private Degree degree;

  @Min(1)
  private int curricularYear;

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  private PersonalityProfile personalityProfile;

  @Column(columnDefinition = "TEXT")
  private String banner;

  @Size(max = 500)
  private String aboutMe;

  @ElementCollection
  private Set<@NotBlank @Size(max = 50) String> skills = new HashSet<>();

  @ElementCollection
  private Set<@NotBlank @Size(max = 50) String> interests = new HashSet<>();

  @Column(columnDefinition = "TEXT")
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

  public void generateInstitutionalEmail(String domain) {
    if (getNumber() == null) {
      throw new IllegalStateException("Student must be saved before generating the institutional email");
    }
    setInstitutionalEmail(getNumber() + "@students." + domain);
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
    if (banner != null && !isValidImage(banner, MAX_BANNER_BYTES)) {
      throw new StudyMatchException(ErrorCode.INVALID_BANNER);
    }
    this.banner = banner;
  }

  private static boolean isValidImage(String image, int maxBytes) {
    Matcher dataUrl = IMAGE_DATA_URL.matcher(image);
    if (!dataUrl.matches()) {
      return false;
    }

    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(dataUrl.group(2));
    } catch (IllegalArgumentException invalidBase64) {
      return false;
    }

    byte[] expectedSignature = dataUrl.group(1).equals("png") ? PNG_SIGNATURE : JPEG_SIGNATURE;
    return bytes.length <= maxBytes && startsWith(bytes, expectedSignature);
  }

  private static boolean startsWith(byte[] bytes, byte[] signature) {
    return bytes.length >= signature.length
        && Arrays.equals(bytes, 0, signature.length, signature, 0, signature.length);
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
    if (pfp != null && !isValidImage(pfp, MAX_PFP_BYTES)) {
      throw new StudyMatchException(ErrorCode.INVALID_PROFILE_PHOTO);
    }
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

  @Override
  public boolean requiresPersonalityTest() {
    return personalityProfile == null;
  }

  @Override
  public Role getRole() {
    return Role.STUDENT;
  }

}
