package com.studymatch.matching;

import java.util.ArrayList;
import java.util.List;

// one candidate answer: the group of every student, with the sizes kept in sync
public final class Grouping {

  private final int[] groupOf;
  private final int[] sizes;

  public Grouping(int[] groupOf, int groupCount) {
    this.groupOf = groupOf.clone();
    this.sizes = new int[groupCount];
    for (int group : groupOf) {
      sizes[group]++;
    }
  }

  public int groupOf(int student) {
    return groupOf[student];
  }

  public int groupCount() {
    return sizes.length;
  }

  public int size(int group) {
    return sizes[group];
  }

  public List<Integer> members(int group) {
    List<Integer> members = new ArrayList<>();
    for (int student = 0; student < groupOf.length; student++) {
      if (groupOf[student] == group) {
        members.add(student);
      }
    }
    return members;
  }

  public void move(int student, int group) {
    sizes[groupOf[student]]--;
    sizes[group]++;
    groupOf[student] = group;
  }

  // sizes don't change, so a swap can never break the size range
  public void swap(int first, int second) {
    int group = groupOf[first];
    groupOf[first] = groupOf[second];
    groupOf[second] = group;
  }

  public Grouping copy() {
    return new Grouping(groupOf, sizes.length);
  }

}
