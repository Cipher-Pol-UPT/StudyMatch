package com.studymatch.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.studymatch.enums.StudyRoomStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
public class StudyRoom {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @NotNull
  private TutorRole tutorRole;

  @NotNull
  private LocalDateTime startsAt;

  @NotNull
  private LocalDateTime endsAt;

  @NotBlank
  private String link;

  @Size(max = 500)
  private String description;

  @Min(2)
  @Max(20)
  private int capacity;

  @Enumerated(EnumType.STRING)
  @NotNull
  private StudyRoomStatus status = StudyRoomStatus.SCHEDULED;

  @Size(max = 500)
  private String cancellationReason;

  @OneToMany(mappedBy = "studyRoom", cascade = CascadeType.ALL, orphanRemoval = true)
  private Set<Participation> participations = new HashSet<>();

  protected StudyRoom() {
  }

  public StudyRoom(TutorRole tutorRole, LocalDateTime startsAt, LocalDateTime endsAt, int capacity,
      String description, String link) {
    validateTimes(startsAt, endsAt);
    this.tutorRole = tutorRole;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.capacity = capacity;
    this.description = description;
    this.link = link;
  }

  public Long getId() {
    return id;
  }

  public TutorRole getTutorRole() {
    return tutorRole;
  }

  public LocalDateTime getStartsAt() {
    return startsAt;
  }

  public LocalDateTime getEndsAt() {
    return endsAt;
  }

  public String getLink() {
    return link;
  }

  public String getDescription() {
    return description;
  }

  public int getCapacity() {
    return capacity;
  }

  public StudyRoomStatus getStatus() {
    if (status == StudyRoomStatus.SCHEDULED && participations.size() >= capacity) {
      return StudyRoomStatus.FULL;
    }
    return status;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public Set<Participation> getParticipations() {
    return Collections.unmodifiableSet(participations);
  }

  public void update(LocalDateTime startsAt, LocalDateTime endsAt, int capacity, String description, String link) {
    requireOpenAndNotStarted();
    validateTimes(startsAt, endsAt);
    if (capacity < participations.size()) {
      throw new StudyMatchException(ErrorCode.CAPACITY_BELOW_PARTICIPANTS,
          Map.of("participants", participations.size()));
    }
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.capacity = capacity;
    this.description = description;
    this.link = link;
  }

  public void cancel(String cancellationReason) {
    requireOpenAndNotStarted();
    this.status = StudyRoomStatus.CANCELLED;
    this.cancellationReason = cancellationReason;
  }

  public void join(Student student) {
    requireOpenAndNotStarted();
    if (student.equals(tutorRole.getStudent())) {
      throw new StudyMatchException(ErrorCode.TUTOR_CANNOT_JOIN_OWN_STUDYROOM);
    }
    if (findParticipation(student) != null) {
      throw new StudyMatchException(ErrorCode.ALREADY_JOINED_STUDYROOM);
    }
    if (getStatus() == StudyRoomStatus.FULL) {
      throw new StudyMatchException(ErrorCode.STUDYROOM_FULL);
    }
    participations.add(new Participation(this, student));
  }

  public void leave(Student student) {
    requireOpenAndNotStarted();
    Participation participation = findParticipation(student);
    if (participation == null) {
      throw new StudyMatchException(ErrorCode.NOT_A_PARTICIPANT);
    }
    participations.remove(participation);
  }

  public void confirmAttendance(Set<Student> attendees) {
    requireOpen();
    if (LocalDateTime.now().isBefore(endsAt)) {
      throw new StudyMatchException(ErrorCode.STUDYROOM_NOT_ENDED);
    }
    participations.forEach(p -> p.setAttended(attendees.contains(p.getStudent())));
    this.status = StudyRoomStatus.COMPLETED;
  }

  private Participation findParticipation(Student student) {
    return participations.stream()
        .filter(p -> p.getStudent().equals(student))
        .findFirst()
        .orElse(null);
  }

  private void requireOpen() {
    if (status == StudyRoomStatus.CANCELLED || status == StudyRoomStatus.COMPLETED) {
      throw new StudyMatchException(ErrorCode.STUDYROOM_CLOSED);
    }
  }

  private void requireOpenAndNotStarted() {
    requireOpen();
    if (!LocalDateTime.now().isBefore(startsAt)) {
      throw new StudyMatchException(ErrorCode.STUDYROOM_ALREADY_STARTED);
    }
  }

  private static void validateTimes(LocalDateTime startsAt, LocalDateTime endsAt) {
    if (startsAt.isBefore(LocalDateTime.now())) {
      throw new StudyMatchException(ErrorCode.STUDYROOM_IN_PAST);
    }
    if (!endsAt.isAfter(startsAt)) {
      throw new StudyMatchException(ErrorCode.STUDYROOM_ENDS_BEFORE_START);
    }
  }

}
