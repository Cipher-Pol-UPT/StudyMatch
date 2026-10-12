package com.studymatch.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.studymatch.enums.GroupStatus;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "student_group", uniqueConstraints = @UniqueConstraint(columnNames = { "assignment_id", "number" }))
public class Group {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "assignment_id")
  @NotNull
  private Assignment assignment;

  @Min(1)
  private int number;

  @Enumerated(EnumType.STRING)
  @NotNull
  private GroupStatus status = GroupStatus.DRAFT;

  @ManyToMany
  private Set<Student> members = new HashSet<>();

  protected Group() {
  }

  public Group(Assignment assignment, int number) {
    if (assignment.getGroupingRule() == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    this.assignment = assignment;
    this.number = number;
  }

  public Long getId() {
    return id;
  }

  public Assignment getAssignment() {
    return assignment;
  }

  public int getNumber() {
    return number;
  }

  public GroupStatus getStatus() {
    return status;
  }

  public void publish() {
    this.status = GroupStatus.PUBLISHED;
  }

  public Set<Student> getMembers() {
    return Collections.unmodifiableSet(members);
  }

  public void addMember(Student student) {
    members.add(student);
  }

  public void removeMember(Student student) {
    members.remove(student);
  }

}
