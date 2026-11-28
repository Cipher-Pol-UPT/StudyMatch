package com.studymatch.models;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;

// a published group's own chat, its members are always the group's students so moving one needs nothing here (GR8)
@Entity
public class GroupChat extends Chat {

  @OneToOne
  @JoinColumn(name = "group_id", unique = true)
  @NotNull
  private Group group;

  protected GroupChat() {
  }

  public GroupChat(Group group) {
    this.group = group;
  }

  public Group getGroup() {
    return group;
  }

  // by number, the user may come from another request than the group
  @Override
  public boolean hasMember(User user) {
    return group.getMembers().stream().anyMatch(member -> member.getNumber().equals(user.getNumber()));
  }

  @Override
  public List<User> getMembers() {
    return new ArrayList<>(group.getMembers());
  }

  // follows the group's number, so renumbering it renames the chat
  public String getName() {
    Assignment assignment = group.getAssignment();
    return assignment.getName() + " · " + assignment.getSubjectEdition().getSubject().getName() + " · Group "
        + group.getNumber();
  }

  // the same for everyone, teachers are never in it
  @Override
  public String getNameFor(User viewer) {
    return getName();
  }

}
