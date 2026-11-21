package com.studymatch.models;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

// a DM, one per pair: the smaller number always goes first, so both directions are the same row (GR8)
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "first_user_number", "second_user_number" }))
public class DirectChat extends Chat {

  @ManyToOne
  @JoinColumn(name = "first_user_number")
  @NotNull
  private User firstUser;

  @ManyToOne
  @JoinColumn(name = "second_user_number")
  @NotNull
  private User secondUser;

  protected DirectChat() {
  }

  public DirectChat(User a, User b) {
    boolean aFirst = a.getNumber() < b.getNumber();
    this.firstUser = aFirst ? a : b;
    this.secondUser = aFirst ? b : a;
  }

  public User getFirstUser() {
    return firstUser;
  }

  public User getSecondUser() {
    return secondUser;
  }

  public User getOtherUser(User user) {
    return firstUser.getNumber().equals(user.getNumber()) ? secondUser : firstUser;
  }

  // by number, the user may come from another request than the chat
  @Override
  public boolean hasMember(User user) {
    return firstUser.getNumber().equals(user.getNumber()) || secondUser.getNumber().equals(user.getNumber());
  }

}
