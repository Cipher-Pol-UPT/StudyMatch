package com.studymatch.dtos;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.studymatch.models.Assignment;
import com.studymatch.models.Group;
import com.studymatch.models.Student;

// one of the student's published groups; blocked members still show (GR5), the page marks them
public record MyGroupDTO(Long id, int number, String subjectCode, String subjectName, Long assignmentId,
    String assignmentName, LocalDate assignmentDate, List<Member> members) {

  public record Member(Long number, String name, String institutionalEmail, boolean blocked) {

    public static Member from(Student student) {
      return new Member(student.getNumber(), student.getName(), student.getInstitutionalEmail(), student.isBlocked());
    }

  }

  public static MyGroupDTO from(Group group) {
    Assignment assignment = group.getAssignment();
    List<Member> members = group.getMembers().stream()
        .map(Member::from)
        .sorted(Comparator.comparing(Member::name).thenComparing(Member::number))
        .toList();
    return new MyGroupDTO(group.getId(), group.getNumber(), assignment.getSubjectEdition().getSubject().getCode(),
        assignment.getSubjectEdition().getSubject().getName(), assignment.getId(), assignment.getName(),
        assignment.getDate(), members);
  }

}
