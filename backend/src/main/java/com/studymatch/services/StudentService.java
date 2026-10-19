package com.studymatch.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.studymatch.dtos.EnrolmentRecordDTO;
import com.studymatch.dtos.FieldErrorDTO;
import com.studymatch.dtos.ImportPreviewRowDTO;
import com.studymatch.dtos.ImportResultRowDTO;
import com.studymatch.dtos.ImportStudentsRequestDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.dtos.PersonalityProfileDTO;
import com.studymatch.dtos.PrivateProfileDTO;
import com.studymatch.dtos.RegisterStudentRequestDTO;
import com.studymatch.dtos.RegisteredStudentDTO;
import com.studymatch.dtos.ResendCredentialsRequestDTO;
import com.studymatch.dtos.StudentProfileDTO;
import com.studymatch.dtos.StudentRecordDTO;
import com.studymatch.dtos.StudentSummaryDTO;
import com.studymatch.dtos.UpdateProfileRequestDTO;
import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.Degree;
import com.studymatch.models.PersonalityProfile;
import com.studymatch.models.Student;
import com.studymatch.models.User;
import com.studymatch.repositories.StudentRepository;
import com.studymatch.security.PasswordPolicy;
import com.studymatch.utils.EmailSender;
import com.studymatch.utils.StudentCsvReader;

import jakarta.validation.Validator;

@Service
public class StudentService {

  private static final int STUDENTS_PER_PAGE = 20;

  private final StudentRepository studentRepository;
  private final EnrolmentService enrolmentService;
  private final TutorRoleService tutorRoleService;
  private final EarnedAchievementService earnedAchievementService;
  private final DegreeService degreeService;
  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final EmailSender emailSender;
  private final Validator validator;
  private final TransactionTemplate transactionTemplate;
  private final String institutionDomain;

  public StudentService(StudentRepository studentRepository, EnrolmentService enrolmentService,
      TutorRoleService tutorRoleService, EarnedAchievementService earnedAchievementService, DegreeService degreeService,
      UserService userService, PasswordEncoder passwordEncoder, EmailSender emailSender, Validator validator,
      TransactionTemplate transactionTemplate, @Value("${studymatch.institution-domain}") String institutionDomain) {
    this.studentRepository = studentRepository;
    this.enrolmentService = enrolmentService;
    this.tutorRoleService = tutorRoleService;
    this.earnedAchievementService = earnedAchievementService;
    this.degreeService = degreeService;
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
    this.emailSender = emailSender;
    this.validator = validator;
    this.transactionTemplate = transactionTemplate;
    this.institutionDomain = institutionDomain;
  }

  // view students (staff): every filter is optional, only the ones given end up in the query
  @Transactional(readOnly = true)
  public PageDTO<StudentSummaryDTO> listStudents(String search, Long degreeId, Integer curricularYear,
      AccountStatus status, int page) {
    List<Specification<Student>> filters = new ArrayList<>();
    if (search != null && !search.isBlank()) {
      filters.add(matchesSearch(search.trim()));
    }
    if (degreeId != null) {
      filters.add((student, query, where) -> where.equal(student.get("degree").get("id"), degreeId));
    }
    if (curricularYear != null) {
      filters.add((student, query, where) -> where.equal(student.get("curricularYear"), curricularYear));
    }
    if (status != null) {
      filters.add((student, query, where) -> where.equal(student.get("accountStatus"), status));
    }

    PageRequest pageRequest = PageRequest.of(Math.max(page, 0), STUDENTS_PER_PAGE, Sort.by("name", "number"));
    return PageDTO.from(studentRepository.findAll(Specification.allOf(filters), pageRequest), this::toSummary);
  }

  // part of the name (any capitals), or the exact number when it's all digits
  private static Specification<Student> matchesSearch(String search) {
    return (student, query, where) -> {
      var byName = where.like(where.lower(student.get("name")), "%" + likeLiteral(search.toLowerCase()) + "%", '\\');
      if (search.chars().allMatch(Character::isDigit) && search.length() < 19) {
        return where.or(byName, where.equal(student.get("number"), Long.valueOf(search)));
      }
      return byName;
    };
  }

  // % and _ are wildcards in LIKE, escaped so they're searched as normal characters
  private static String likeLiteral(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private StudentSummaryDTO toSummary(Student student) {
    Double attendance = student.hasActiveStatute() ? null : enrolmentService.getAttendanceRate(student);
    return new StudentSummaryDTO(student.getNumber(), student.getName(), student.getDegree().getName(),
        student.getCurricularYear(), attendance, enrolmentService.getAverageGrade(student),
        PersonalityProfileDTO.from(student.getPersonalityProfile()), student.getAccountStatus());
  }

  // view students (staff), picking one: the normal profile plus the staff-only parts
  @Transactional(readOnly = true)
  public StudentRecordDTO getRecord(Long studentNumber, User staff) {
    Student student = getByNumber(studentNumber);
    List<EnrolmentRecordDTO> enrolments = enrolmentService.getEnrolments(student).stream()
        .map(EnrolmentRecordDTO::from)
        .toList();

    return new StudentRecordDTO(getProfile(studentNumber, staff), student.getPersonalEmail(),
        student.getAccountStatus(), student.getStatuteType(), student.getStatuteEndDate(),
        enrolmentService.getAverageGrade(student), enrolmentService.getCategoryLevels(student), enrolments);
  }

  @Transactional(readOnly = true)
  public StudentProfileDTO getProfile(Long studentNumber, User viewer) {
    Student student = getByNumber(studentNumber);
    PrivateProfileDTO privateProfile = canSeePrivateProfile(student, viewer) ? getPrivateProfile(student) : null;
    return toProfile(student, privateProfile);
  }

  @Transactional
  public StudentProfileDTO updateProfile(Long studentNumber, UpdateProfileRequestDTO changes) {
    Student student = getByNumber(studentNumber);
    student.setBanner(changes.banner());
    student.setPfp(changes.pfp());
    student.setAboutMe(changes.aboutMe());
    student.setSkills(changes.skills());
    student.setInterests(changes.interests());
    student.setLanguages(changes.languages());
    studentRepository.save(student);

    return toProfile(student, getPrivateProfile(student));
  }

  @Transactional
  public PersonalityProfileDTO takePersonalityTest(Long studentNumber, List<Integer> answers) {
    Student student = getByNumber(studentNumber);
    // GR1: once per academic year, the reset comes with Start Academic Year (#66)
    if (!student.requiresPersonalityTest()) {
      throw new StudyMatchException(ErrorCode.PERSONALITY_TEST_ALREADY_TAKEN);
    }
    student.setPersonalityProfile(PersonalityProfile.fromAnswers(answers));
    return PersonalityProfileDTO.from(studentRepository.save(student).getPersonalityProfile());
  }

  // register student (manual): annotations were already checked by @Valid, the first other problem is thrown
  public RegisteredStudentDTO registerStudent(RegisterStudentRequestDTO request) {
    List<FieldErrorDTO> problems = checkAgainstSystem(request, Set.of(), Set.of());
    if (!problems.isEmpty()) {
      FieldErrorDTO first = problems.getFirst();
      throw new StudyMatchException(ErrorCode.valueOf(first.code()), first.params());
    }
    return create(request);
  }

  // register student (CSV, step 1): nothing is saved, every row just gets its errors
  public List<ImportPreviewRowDTO> previewImport(List<StudentCsvReader.Row> rows) {
    Set<String> emailsInFile = new HashSet<>();
    List<ImportPreviewRowDTO> preview = new ArrayList<>();
    for (StudentCsvReader.Row row : rows) {
      preview.add(new ImportPreviewRowDTO(row.row(), row.student(), validateRow(row.student(), row.errors(), emailsInFile)));
    }
    return preview;
  }

  // register student (CSV, step 2): the confirmed rows are checked again (never trust the client), then created one by one
  public List<ImportResultRowDTO> importStudents(List<ImportStudentsRequestDTO.Row> rows) {
    if (rows.size() > StudentCsvReader.MAX_ROWS) {
      throw new StudyMatchException(ErrorCode.CSV_TOO_MANY_ROWS, Map.of("max", StudentCsvReader.MAX_ROWS));
    }

    Set<String> emailsInFile = new HashSet<>();
    List<ImportResultRowDTO> results = new ArrayList<>();
    for (ImportStudentsRequestDTO.Row row : rows) {
      if (row.student() == null) {
        results.add(ImportResultRowDTO.rejected(row.row(), List.of(new FieldErrorDTO("student", "NOT_NULL", Map.of()))));
        continue;
      }
      List<FieldErrorDTO> errors = validateRow(row.student(), List.of(), emailsInFile);
      if (!errors.isEmpty()) {
        results.add(ImportResultRowDTO.rejected(row.row(), errors));
        continue;
      }
      try {
        results.add(ImportResultRowDTO.created(row.row(), create(row.student())));
      } catch (StudyMatchException e) {
        results.add(ImportResultRowDTO.rejected(row.row(),
            List.of(new FieldErrorDTO("personalEmail", e.getCode().name(), e.getParams()))));
      }
    }
    return results;
  }

  // only while the student hasn't set their own password, and always with a new one-time password (we only keep hashes)
  @Transactional
  public void resendCredentials(Long studentNumber, ResendCredentialsRequestDTO request) {
    Student student = getByNumber(studentNumber);
    if (!student.isMustChangePassword()) {
      throw new StudyMatchException(ErrorCode.CREDENTIALS_ALREADY_USED);
    }

    String newEmail = request == null ? null : request.personalEmail();
    if (newEmail != null && !newEmail.equals(student.getPersonalEmail())) {
      if (userService.isPersonalEmailTaken(newEmail)) {
        throw new StudyMatchException(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS);
      }
      student.setPersonalEmail(newEmail);
    }

    String oneTimePassword = PasswordPolicy.generateOneTimePassword();
    student.setPassword(passwordEncoder.encode(oneTimePassword));
    studentRepository.save(student);

    // failing again rolls all of this back, so the old email and password stay as they were
    try {
      emailSender.sendAccountCredentials(student.getPersonalEmail(), student.getName(), student.getInstitutionalEmail(),
          oneTimePassword);
    } catch (MailException e) {
      throw new StudyMatchException(ErrorCode.EMAIL_NOT_SENT);
    }
  }

  // annotations + the rules that need the database; earlier emails in the same file count as taken
  private List<FieldErrorDTO> validateRow(RegisterStudentRequestDTO student, List<FieldErrorDTO> readErrors,
      Set<String> emailsInFile) {
    List<FieldErrorDTO> errors = new ArrayList<>(readErrors);
    Set<String> readFields = fieldsOf(readErrors);
    validator.validate(student).stream()
        .map(FieldErrorDTO::from)
        .filter(error -> !readFields.contains(error.field()))
        .forEach(errors::add);
    errors.addAll(checkAgainstSystem(student, fieldsOf(errors), emailsInFile));

    if (student.personalEmail() != null) {
      emailsInFile.add(student.personalEmail());
    }
    return errors;
  }

  // fields that are already wrong are skipped, no point looking up a degree that's blank
  private List<FieldErrorDTO> checkAgainstSystem(RegisterStudentRequestDTO student, Set<String> wrongFields,
      Set<String> emailsInFile) {
    List<FieldErrorDTO> problems = new ArrayList<>();

    if (!wrongFields.contains("degreeProgramme")) {
      Optional<Degree> degree = degreeService.findByName(student.degreeProgramme());
      if (degree.isEmpty()) {
        problems.add(new FieldErrorDTO("degreeProgramme", ErrorCode.DEGREE_NOT_FOUND.name(), Map.of()));
      } else if (!wrongFields.contains("curricularYear") && !degree.get().hasCurricularYear(student.curricularYear())) {
        problems.add(new FieldErrorDTO("curricularYear", ErrorCode.CURRICULAR_YEAR_NOT_IN_DEGREE.name(),
            Map.of("years", degree.get().getCurricularYears())));
      }
    }

    if (!wrongFields.contains("personalEmail")) {
      if (userService.isPersonalEmailTaken(student.personalEmail())) {
        problems.add(new FieldErrorDTO("personalEmail", ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS.name(), Map.of()));
      } else if (emailsInFile.contains(student.personalEmail())) {
        problems.add(new FieldErrorDTO("personalEmail", ErrorCode.DUPLICATE_IN_FILE.name(), Map.of()));
      }
    }
    return problems;
  }

  private static Set<String> fieldsOf(List<FieldErrorDTO> errors) {
    return errors.stream().map(FieldErrorDTO::field).collect(Collectors.toSet());
  }

  // each student in its own transaction, committed before the email so a mail problem never undoes the account
  private RegisteredStudentDTO create(RegisterStudentRequestDTO request) {
    Degree degree = degreeService.getByName(request.degreeProgramme());
    String oneTimePassword = PasswordPolicy.generateOneTimePassword();
    String hash = passwordEncoder.encode(oneTimePassword);

    Student student;
    try {
      student = transactionTemplate.execute(status -> {
        Student saved = studentRepository.save(
            new Student(request.personalEmail(), request.name(), hash, degree, request.curricularYear()));
        saved.generateInstitutionalEmail(institutionDomain);
        return saved;
      });
    } catch (DataIntegrityViolationException e) {
      // someone else took this personal email between the check and the save
      throw new StudyMatchException(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS);
    }

    return RegisteredStudentDTO.from(student, sendCredentials(student, oneTimePassword));
  }

  private boolean sendCredentials(Student student, String oneTimePassword) {
    try {
      emailSender.sendAccountCredentials(student.getPersonalEmail(), student.getName(), student.getInstitutionalEmail(),
          oneTimePassword);
      return true;
    } catch (MailException e) {
      return false;
    }
  }

  private Student getByNumber(Long studentNumber) {
    return studentRepository.findById(studentNumber)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  private boolean canSeePrivateProfile(Student student, User viewer) {
    boolean isOwnProfile = viewer.getNumber().equals(student.getNumber());
    boolean isStaff = viewer.getRole() == Role.STAFF;
    boolean isTheirTeacher = viewer.getRole() == Role.TEACHER
        && enrolmentService.isTaughtBy(student, viewer.getNumber());

    return isOwnProfile || isStaff || isTheirTeacher;
  }

  private PrivateProfileDTO getPrivateProfile(Student student) {
    Double attendance = student.hasActiveStatute() ? null : enrolmentService.getAttendanceRate(student);

    return new PrivateProfileDTO(student.getInstitutionalEmail(), student.getDegree().getName(),
        student.getCurricularYear(), PersonalityProfileDTO.from(student.getPersonalityProfile()), attendance,
        enrolmentService.hasAcademicHistory(student));
  }

  private StudentProfileDTO toProfile(Student student, PrivateProfileDTO privateProfile) {
    return StudentProfileDTO.from(student, tutorRoleService.getTutorBadges(student),
        earnedAchievementService.getAchievements(student), privateProfile);
  }

}
