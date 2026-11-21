package com.studymatch.services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.GroupPreviewDTO;
import com.studymatch.enums.GroupStatus;
import com.studymatch.enums.Language;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.matching.Candidate;
import com.studymatch.matching.GroupGenerator;
import com.studymatch.matching.NoValidSplitException;
import com.studymatch.matching.UnmetSeparationsException;
import com.studymatch.models.AcademicYear;
import com.studymatch.models.Assignment;
import com.studymatch.models.Enrolment;
import com.studymatch.models.Group;
import com.studymatch.models.GroupingRule;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Separation;
import com.studymatch.models.Student;
import com.studymatch.repositories.GroupRepository;

@Service
public class GroupService {

  private final GroupRepository groupRepository;
  private final AssignmentService assignmentService;
  private final EnrolmentService enrolmentService;
  private final GroupGenerator groupGenerator;
  private final Random random = new SecureRandom();

  public GroupService(GroupRepository groupRepository, AssignmentService assignmentService,
      EnrolmentService enrolmentService, GroupGenerator groupGenerator) {
    this.groupRepository = groupRepository;
    this.assignmentService = assignmentService;
    this.enrolmentService = enrolmentService;
    this.groupGenerator = groupGenerator;
  }

  // generate groups: the new groups replace the drafts, the published ones stay until the teacher confirms
  @Transactional
  public List<GroupPreviewDTO> generateGroups(Long subjectEditionId, Long assignmentId, boolean replacePublished) {
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    GroupingRule rule = assignment.getGroupingRule();
    if (rule == null) {
      throw new StudyMatchException(ErrorCode.ASSIGNMENT_NOT_GROUP_WORK);
    }
    if (!replacePublished && groupRepository.existsByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED)) {
      throw new StudyMatchException(ErrorCode.GROUPS_ALREADY_PUBLISHED);
    }
    // blocked accounts aren't placed in new groups (GR5)
    List<Enrolment> enrolments = enrolmentService.listEnrolments(subjectEditionId).stream()
        .filter(enrolment -> !enrolment.getStudent().isBlocked())
        .sorted(Comparator.comparing(enrolment -> enrolment.getStudent().getNumber()))
        .toList();
    if (enrolments.size() < rule.getMinSize()) {
      throw new StudyMatchException(ErrorCode.NOT_ENOUGH_STUDENTS_FOR_GROUPS);
    }
    Long categoryId = assignment.getSubjectEdition().getSubject().getCategory().getId();
    List<Candidate> candidates = enrolments.stream().map(enrolment -> toCandidate(enrolment, categoryId)).toList();
    List<List<Integer>> groups = generate(candidates, separations(rule, enrolments), rule);

    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
    // the old drafts have to be gone before the new ones take their numbers
    groupRepository.flush();
    List<GroupPreviewDTO> previews = new ArrayList<>();
    for (int index = 0; index < groups.size(); index++) {
      Group group = new Group(assignment, index + 1);
      groups.get(index).forEach(student -> group.addMember(enrolments.get(student).getStudent()));
      groupRepository.save(group);
      previews.add(preview(index + 1, groups.get(index), enrolments));
    }
    return previews;
  }

  // generate groups, confirm: the drafts replace whatever was published before
  @Transactional
  public void publishGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    List<Group> drafts = groupRepository.findByAssignmentAndStatusOrderByNumber(assignment, GroupStatus.DRAFT);
    if (drafts.isEmpty()) {
      throw new StudyMatchException(ErrorCode.NO_DRAFT_GROUPS);
    }
    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.PUBLISHED);
    groupRepository.flush();
    drafts.forEach(Group::publish);
    // telling the students about their groups comes with notifications (#144)
  }

  // generate groups, cancel: the published groups, if any, stay as they were
  @Transactional
  public void discardGroups(Long subjectEditionId, Long assignmentId) {
    Assignment assignment = assignmentService.getAssignment(subjectEditionId, assignmentId);
    groupRepository.deleteByAssignmentAndStatus(assignment, GroupStatus.DRAFT);
  }

  private List<List<Integer>> generate(List<Candidate> candidates, List<int[]> separations, GroupingRule rule) {
    try {
      return groupGenerator.generate(candidates, separations, rule.getMinSize(), rule.getOptimalSize(),
          rule.getMaxSize(), rule.isSameClass(), random);
    } catch (NoValidSplitException e) {
      throw new StudyMatchException(ErrorCode.NO_VALID_GROUP_SPLIT);
    } catch (UnmetSeparationsException e) {
      throw new StudyMatchException(ErrorCode.SEPARATIONS_CANNOT_BE_MET);
    }
  }

  private Candidate toCandidate(Enrolment enrolment, Long categoryId) {
    Student student = enrolment.getStudent();
    EnrolmentService.GroupingEvidence evidence = enrolmentService.getGroupingEvidence(student, categoryId);
    int languages = 0;
    for (Language language : student.getLanguages()) {
      languages |= 1 << language.ordinal();
    }
    return new Candidate(traits(student.getPersonalityProfile()), evidence.level(), evidence.attendance(),
        languages, enrolment.getClassSection().getCode());
  }

  // the matching works with emotional stability, the opposite of neuroticism
  private static Candidate.Traits traits(PersonalityProfile profile) {
    if (profile == null) {
      return null;
    }
    return new Candidate.Traits(profile.getOpenness(), profile.getConscientiousness(), profile.getExtraversion(),
        profile.getAgreeableness(), 1 - profile.getNeuroticism());
  }

  // as positions in the list; a pair with a blocked student no longer matters
  private static List<int[]> separations(GroupingRule rule, List<Enrolment> enrolments) {
    Map<Long, Integer> position = new HashMap<>();
    for (int i = 0; i < enrolments.size(); i++) {
      position.put(enrolments.get(i).getStudent().getNumber(), i);
    }
    List<int[]> pairs = new ArrayList<>();
    for (Separation separation : rule.getSeparations()) {
      Integer first = position.get(separation.getFirstStudent().getNumber());
      Integer second = position.get(separation.getSecondStudent().getNumber());
      if (first != null && second != null) {
        pairs.add(new int[] { first, second });
      }
    }
    return pairs;
  }

  private static GroupPreviewDTO preview(int number, List<Integer> students, List<Enrolment> enrolments) {
    Set<Language> shared = EnumSet.allOf(Language.class);
    List<GroupPreviewDTO.Member> members = new ArrayList<>();
    for (int student : students) {
      Enrolment enrolment = enrolments.get(student);
      shared.retainAll(enrolment.getStudent().getLanguages());
      members.add(new GroupPreviewDTO.Member(enrolment.getStudent().getNumber(), enrolment.getStudent().getName(),
          enrolment.getClassSection().getCode()));
    }
    members.sort(Comparator.comparing(GroupPreviewDTO.Member::name).thenComparing(GroupPreviewDTO.Member::number));
    return new GroupPreviewDTO(number, members.size(), List.copyOf(shared), members);
  }

  // analytics: drafts can still change, so only the published groups are measured
  public List<Group> listPublishedGroups(AcademicYear academicYear) {
    return groupRepository.findByAssignment_SubjectEdition_AcademicYearAndStatus(academicYear, GroupStatus.PUBLISHED);
  }

}
