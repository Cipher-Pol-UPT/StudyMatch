package com.studymatch.models;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.studymatch.enums.AssignmentType;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;

class AssignmentTest {

  private SubjectEdition edition;
  private GroupingRule groupingRule;

  @BeforeEach
  void setUp() {
    edition = ModelTestData.subjectEdition();
    groupingRule = new GroupingRule(2, 3, 4, false);
  }

  private Assignment newAssignment(AssignmentType type, GroupingRule rule) {
    return new Assignment(edition, "Test Assignment", type, LocalDate.now(), new BigDecimal("0.30"), rule);
  }

  @Test
  @DisplayName("An individual project has no grouping rule")
  void individualProject() {
    // Act
    Assignment assignment = newAssignment(AssignmentType.PROJECT, null);

    // Assert
    assertNull(assignment.getGroupingRule());
  }

  @Test
  @DisplayName("A project can be group work")
  void groupProject() {
    // Act
    Assignment assignment = newAssignment(AssignmentType.PROJECT, groupingRule);

    // Assert
    assertSame(groupingRule, assignment.getGroupingRule());
  }

  @ParameterizedTest(name = "{0} can't be group work")
  @EnumSource(value = AssignmentType.class, names = { "EXAM", "PRACTICAL_EXAM" })
  @DisplayName("Exams and practical exams can't be group work")
  void examsCantBeGroupWork(AssignmentType type) {
    // Act
    StudyMatchException e = assertThrows(StudyMatchException.class, () -> newAssignment(type, groupingRule));

    // Assert
    assertEquals(ErrorCode.GROUP_WORK_NOT_ALLOWED_FOR_EXAMS, e.getCode());
  }

  @ParameterizedTest(name = "individual {0} is fine")
  @EnumSource(value = AssignmentType.class, names = { "EXAM", "PRACTICAL_EXAM" })
  @DisplayName("Individual exams and practical exams are allowed")
  void individualExamsAreFine(AssignmentType type) {
    // the rule only blocks group work, not exams themselves
    assertDoesNotThrow(() -> newAssignment(type, null));
  }

}
