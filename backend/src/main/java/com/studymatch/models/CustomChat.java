package com.studymatch.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.utils.ImageDataUrls;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// a group chat someone made, the owner is one of the members (GR8)
@Entity
public class CustomChat extends Chat {

  public static final int MIN_MEMBERS = 3;

  public static final int MAX_MEMBERS = 20;

  public static final int MAX_PICTURE_BYTES = 1024 * 1024;

  @NotBlank
  @Size(max = 50)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String picture;

  @ManyToOne
  @NotNull
  private User owner;

  // in the order they joined, the first one after the owner takes over if the owner leaves (#232).
  // no unique (chat, user): someone leaving shifts the rest up a row at a time, and halfway one of them is in two
  // rows. nobody's in twice because adding skips who's in, and changes are locked one at a time (data.sql drops it)
  @ManyToMany
  @JoinTable(name = "custom_chat_member", joinColumns = @JoinColumn(name = "chat_id"),
      inverseJoinColumns = @JoinColumn(name = "user_number"))
  @OrderColumn(name = "position")
  private List<User> members = new ArrayList<>();

  protected CustomChat() {
  }

  // others is everyone but the owner, already checked as people the owner can message
  public CustomChat(String name, String picture, User owner, List<User> others) {
    checkSize(others.size() + 1);
    this.name = name;
    setPicture(picture);
    this.owner = owner;
    this.members.add(owner);
    this.members.addAll(others);
  }

  // size counts everyone with the owner. the error's min is the others to pick (2), max is everyone (20), as the
  // creating screen says them. public so the service checks it before looking anyone up
  public static void checkSize(int size) {
    if (size < MIN_MEMBERS) {
      throw new StudyMatchException(ErrorCode.NOT_ENOUGH_GROUP_CHAT_MEMBERS, Map.of("min", MIN_MEMBERS - 1));
    }
    checkNotTooMany(size);
  }

  // adding only has the top limit, the 3 is for creating. a chat people left or were removed from can be smaller
  public static void checkNotTooMany(int size) {
    if (size > MAX_MEMBERS) {
      throw new StudyMatchException(ErrorCode.TOO_MANY_GROUP_CHAT_MEMBERS, Map.of("max", MAX_MEMBERS));
    }
  }

  // anyone already in is skipped, the rest go at the end so the list stays in joining order (GR8).
  // gives back who was really added
  public List<User> addMembers(List<User> people) {
    List<User> added = people.stream().filter(person -> !hasMember(person)).toList();
    checkNotTooMany(members.size() + added.size());
    members.addAll(added);
    return added;
  }

  // by number, someone who isn't in changes nothing. they can be added again later, at the end
  public void removeMember(User member) {
    members.removeIf(current -> current.getNumber().equals(member.getNumber()));
  }

  public boolean isOwner(User user) {
    return owner.getNumber().equals(user.getNumber());
  }

  public String getName() {
    return name;
  }

  public String getPicture() {
    return picture;
  }

  // checked like the profile photo
  public void setPicture(String picture) {
    if (picture != null && !ImageDataUrls.isValidImage(picture, MAX_PICTURE_BYTES)) {
      throw new StudyMatchException(ErrorCode.INVALID_GROUP_CHAT_PICTURE);
    }
    this.picture = picture;
  }

  public User getOwner() {
    return owner;
  }

  // by number, the user may come from another request than the chat
  @Override
  public boolean hasMember(User user) {
    return members.stream().anyMatch(member -> member.getNumber().equals(user.getNumber()));
  }

  @Override
  public List<User> getMembers() {
    return Collections.unmodifiableList(members);
  }

  @Override
  public String getNameFor(User viewer) {
    return name;
  }

}
