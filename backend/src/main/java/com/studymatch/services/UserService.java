package com.studymatch.services;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studymatch.dtos.AccountSummaryDTO;
import com.studymatch.dtos.BlockImpactDTO;
import com.studymatch.dtos.BlockedAccountDTO;
import com.studymatch.dtos.ChangePasswordRequestDTO;
import com.studymatch.dtos.ChatContactDTO;
import com.studymatch.dtos.CreateAccountRequestDTO;
import com.studymatch.dtos.PageDTO;
import com.studymatch.dtos.ResetPasswordRequestDTO;
import com.studymatch.enums.AccountStatus;
import com.studymatch.enums.NotificationType;
import com.studymatch.enums.Role;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.utils.EmailSender;
import com.studymatch.utils.SearchPatterns;
import com.studymatch.models.Staff;
import com.studymatch.models.Student;
import com.studymatch.models.Teacher;
import com.studymatch.models.User;
import com.studymatch.repositories.UserRepository;
import com.studymatch.security.PasswordPolicy;

@Service
public class UserService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int ACCOUNTS_PER_PAGE = 20;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailSender emailSender;
  private final StudyRoomService studyRoomService;
  private final TutorApplicationService tutorApplicationService;
  private final SubjectEditionService subjectEditionService;
  private final NotificationService notificationService;
  private final String institutionDomain;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailSender emailSender,
      StudyRoomService studyRoomService, TutorApplicationService tutorApplicationService,
      SubjectEditionService subjectEditionService, NotificationService notificationService,
      @Value("${studymatch.institution-domain}") String institutionDomain) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailSender = emailSender;
    this.studyRoomService = studyRoomService;
    this.tutorApplicationService = tutorApplicationService;
    this.subjectEditionService = subjectEditionService;
    this.notificationService = notificationService;
    this.institutionDomain = institutionDomain;
  }

  public Optional<User> findByInstitutionalEmail(String institutionalEmail) {
    return userRepository.findByInstitutionalEmail(institutionalEmail.trim().toLowerCase());
  }

  public User getByInstitutionalEmail(String institutionalEmail) {
    return findByInstitutionalEmail(institutionalEmail)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  public User getByNumber(Long number) {
    return userRepository.findById(number).orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // block account (admin): every role, both filters optional
  @Transactional(readOnly = true)
  public PageDTO<AccountSummaryDTO> listAccounts(String search, AccountStatus status, int page) {
    List<Specification<User>> filters = new ArrayList<>();
    if (search != null && !search.isBlank()) {
      filters.add(matchesSearch(search.trim()));
    }
    if (status != null) {
      filters.add((user, query, where) -> where.equal(user.get("accountStatus"), status));
    }

    PageRequest pageRequest = PageRequest.of(Math.max(page, 0), ACCOUNTS_PER_PAGE, Sort.by("name", "number"));
    return PageDTO.from(userRepository.findAll(Specification.allOf(filters), pageRequest), AccountSummaryDTO::from);
  }

  // part of the name or institutional email, or the exact number when it's all digits
  private static Specification<User> matchesSearch(String search) {
    return (user, query, where) -> {
      String pattern = SearchPatterns.containing(search);
      var byText = where.or(
          where.like(where.lower(user.get("name")), pattern, SearchPatterns.ESCAPE),
          where.like(where.lower(user.get("institutionalEmail")), pattern, SearchPatterns.ESCAPE));
      if (search.chars().allMatch(Character::isDigit) && search.length() < 19) {
        return where.or(byText, where.equal(user.get("number"), Long.valueOf(search)));
      }
      return byText;
    };
  }

  // who you can message: Students and Teachers that aren't blocked, never yourself (GR8). the only place this rule lives
  private static Specification<User> chatContactOf(User user) {
    return Specification.allOf(
        (contact, query, where) -> contact.type().in(Student.class, Teacher.class),
        (contact, query, where) -> where.equal(contact.get("accountStatus"), AccountStatus.ACTIVE),
        (contact, query, where) -> where.notEqual(contact.get("number"), user.getNumber()));
  }

  // start a DM
  @Transactional(readOnly = true)
  public PageDTO<ChatContactDTO> listChatContacts(String search, User user, int page) {
    Specification<User> filter = chatContactOf(user);
    if (search != null && !search.isBlank()) {
      filter = filter.and(matchesSearch(search.trim()));
    }

    PageRequest pageRequest = PageRequest.of(Math.max(page, 0), ACCOUNTS_PER_PAGE, Sort.by("name", "number"));
    return PageDTO.from(userRepository.findAll(filter, pageRequest), ChatContactDTO::from);
  }

  // the same rule as the search, so someone it wouldn't offer looks the same as nobody
  @Transactional(readOnly = true)
  public User getChatContact(Long number, User user) {
    Specification<User> byNumber = (contact, query, where) -> where.equal(contact.get("number"), number);
    return userRepository.findOne(chatContactOf(user).and(byNumber))
        .orElseThrow(() -> new StudyMatchException(ErrorCode.NOT_FOUND));
  }

  // the people picked for a group chat in the order picked, one query for all. anyone that rule leaves out is
  // NOT_FOUND like above
  @Transactional(readOnly = true)
  public List<User> getChatContacts(Set<Long> numbers, User user) {
    if (numbers.isEmpty()) {
      return List.of();
    }
    Specification<User> byNumbers = (contact, query, where) -> contact.get("number").in(numbers);
    Map<Long, User> found = userRepository.findAll(chatContactOf(user).and(byNumbers)).stream()
        .collect(Collectors.toMap(User::getNumber, Function.identity()));
    if (found.size() != numbers.size()) {
      throw new StudyMatchException(ErrorCode.NOT_FOUND);
    }
    return numbers.stream().map(found::get).toList();
  }

  // what blocking would cancel, the queries just come back empty for roles that have none of it
  @Transactional(readOnly = true)
  public BlockImpactDTO getBlockImpact(Long number) {
    User user = getByNumber(number);
    user.checkCanBeBlocked();
    return new BlockImpactDTO(AccountSummaryDTO.from(user), studyRoomService.countUpcoming(number),
        tutorApplicationService.countPending(number), subjectEditionService.listCurrentTaughtBy(number));
  }

  // the session ends on its next request, AccountStateFilter checks the status every time
  @Transactional
  public BlockedAccountDTO blockAccount(Long number, String reason) {
    User user = getByNumber(number);
    user.block(reason);
    userRepository.save(user);

    int cancelledStudyRooms = studyRoomService.cancelUpcoming(number);
    int cancelledTutorApplications = tutorApplicationService.cancelPending(number);
    return new BlockedAccountDTO(AccountSummaryDTO.from(user), cancelledStudyRooms, cancelledTutorApplications);
  }

  // the cancelled StudyRooms stay cancelled
  @Transactional
  public AccountSummaryDTO unblockAccount(Long number) {
    User user = getByNumber(number);
    user.unblock();
    notificationService.notify(user, NotificationType.ACCOUNT_UNBLOCKED, null, null, Map.of());
    return AccountSummaryDTO.from(userRepository.save(user));
  }

  // account + email are all or nothing: if the email fails, the account is rolled back so the admin can fix it and retry
  @Transactional
  public User createAccount(CreateAccountRequestDTO request) {
    if (request.type() != Role.TEACHER && request.type() != Role.STAFF) {
      throw new StudyMatchException(ErrorCode.ACCOUNT_TYPE_NOT_ALLOWED);
    }

    String institutionalEmail = request.username() + "@" + institutionDomain;
    if (userRepository.existsByInstitutionalEmail(institutionalEmail)) {
      throw new StudyMatchException(ErrorCode.INSTITUTIONAL_EMAIL_ALREADY_EXISTS);
    }
    if (isPersonalEmailTaken(request.personalEmail())) {
      throw new StudyMatchException(ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS);
    }

    String oneTimePassword = PasswordPolicy.generateOneTimePassword();
    String hash = passwordEncoder.encode(oneTimePassword);
    User user = request.type() == Role.TEACHER
        ? new Teacher(request.personalEmail(), institutionalEmail, request.name(), hash)
        : new Staff(request.personalEmail(), institutionalEmail, request.name(), hash);
    user = userRepository.save(user);

    try {
      emailSender.sendAccountCredentials(user.getPersonalEmail(), user.getName(), institutionalEmail, oneTimePassword);
    } catch (MailException e) {
      throw new StudyMatchException(ErrorCode.EMAIL_NOT_SENT);
    }
    return user;
  }

  // personal emails are unique across every kind of user, so other services check here
  public boolean isPersonalEmailTaken(String personalEmail) {
    return userRepository.existsByPersonalEmail(personalEmail.trim().toLowerCase());
  }

  public User changePassword(String institutionalEmail, ChangePasswordRequestDTO request) {
    User user = getByInstitutionalEmail(institutionalEmail);

    // on the forced first change the one-time password was just checked at login, so it's not asked again
    if (!user.isMustChangePassword()) {
      String currentPassword = request.currentPassword();
      if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
        throw new StudyMatchException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
      }
    }

    String newPassword = request.newPassword();
    if (!PasswordPolicy.fitsInBcrypt(newPassword)) {
      throw new StudyMatchException(ErrorCode.PASSWORD_TOO_LONG);
    }
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new StudyMatchException(ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT);
    }

    user.setPassword(passwordEncoder.encode(newPassword));
    user.setMustChangePassword(false);
    return userRepository.save(user);
  }

  public void requestPasswordReset(String institutionalEmail) {
    // hashed before knowing if the account exists, so both cases take the same time
    String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
    String codeHash = passwordEncoder.encode(code);

    Optional<User> found = findByInstitutionalEmail(institutionalEmail);
    if (found.isEmpty() || found.get().isBlocked()) {
      return;
    }

    User user = found.get();
    user.startPasswordReset(codeHash);
    userRepository.save(user);
    // personal email on purpose: it's the recovery contact, the institutional one might need the account to read it
    emailSender.sendPasswordResetCode(user.getPersonalEmail(), code);
  }

  public void resetPassword(ResetPasswordRequestDTO request) {
    // unknown email, no code or an expired one all get the same answer
    User user = findByInstitutionalEmail(request.email())
        .filter(User::hasActiveResetCode)
        .orElseThrow(() -> new StudyMatchException(ErrorCode.RESET_CODE_INVALID));

    if (!passwordEncoder.matches(request.code(), user.getResetCodeHash())) {
      user.registerFailedResetAttempt();
      userRepository.save(user);
      throw new StudyMatchException(ErrorCode.RESET_CODE_INVALID);
    }
    if (!PasswordPolicy.fitsInBcrypt(request.newPassword())) {
      throw new StudyMatchException(ErrorCode.PASSWORD_TOO_LONG);
    }

    user.setPassword(passwordEncoder.encode(request.newPassword()));
    user.setMustChangePassword(false);
    user.clearPasswordReset();
    userRepository.save(user);
  }

}
