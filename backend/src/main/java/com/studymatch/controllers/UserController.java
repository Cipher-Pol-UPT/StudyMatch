package com.studymatch.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.ChangePasswordRequestDTO;
import com.studymatch.dtos.CreateAccountRequestDTO;
import com.studymatch.dtos.CreatedAccountDTO;
import com.studymatch.dtos.CurrentUserDTO;
import com.studymatch.dtos.ForgotPasswordRequestDTO;
import com.studymatch.dtos.LoginRequestDTO;
import com.studymatch.dtos.ResetPasswordRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.ErrorCodes;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.User;
import com.studymatch.services.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/")
@Tag(name = "Users", description = "Login, passwords and staff/teacher accounts. Logout is `POST /users/logout` (204)")
public class UserController {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;

  public UserController(UserService userService, AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.userService = userService;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  @PostMapping("login")
  @Operation(summary = "Log in with the institutional email, starts the session")
  @ErrorCodes({ ErrorCode.INVALID_CREDENTIALS, ErrorCode.ACCOUNT_BLOCKED })
  public ResponseEntity<CurrentUserDTO> login(@Valid @RequestBody LoginRequestDTO loginRequest,
      HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password()));

    User user = userService.getByInstitutionalEmail(authentication.getName());
    if (user.isBlocked()) {
      throw new StudyMatchException(ErrorCode.ACCOUNT_BLOCKED);
    }

    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    return ResponseEntity.ok(CurrentUserDTO.from(user));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Create a staff or teacher account, the one-time password goes by email")
  @ErrorCodes({ ErrorCode.ACCOUNT_TYPE_NOT_ALLOWED, ErrorCode.INSTITUTIONAL_EMAIL_ALREADY_EXISTS,
      ErrorCode.PERSONAL_EMAIL_ALREADY_EXISTS, ErrorCode.EMAIL_NOT_SENT })
  public ResponseEntity<CreatedAccountDTO> createAccount(@Valid @RequestBody CreateAccountRequestDTO createRequest) {
    User user = userService.createAccount(createRequest);
    return ResponseEntity.status(HttpStatus.CREATED).body(CreatedAccountDTO.from(user));
  }

  @GetMapping("me")
  @Operation(summary = "The logged-in user, with the steps they still owe (password change, personality test)")
  public ResponseEntity<CurrentUserDTO> me(Authentication authentication) {
    return ResponseEntity.ok(CurrentUserDTO.from(userService.getByInstitutionalEmail(authentication.getName())));
  }

  @PutMapping("change-password")
  @Operation(summary = "Change your own password")
  @ErrorCodes({ ErrorCode.CURRENT_PASSWORD_INCORRECT, ErrorCode.NEW_PASSWORD_SAME_AS_CURRENT,
      ErrorCode.PASSWORD_TOO_LONG })
  public ResponseEntity<CurrentUserDTO> changePassword(@Valid @RequestBody ChangePasswordRequestDTO changeRequest,
      Authentication authentication, HttpServletRequest request) {
    User user = userService.changePassword(authentication.getName(), changeRequest);

    // new access level (the gate opens), so new session id, same as on login
    request.changeSessionId();

    return ResponseEntity.ok(CurrentUserDTO.from(user));
  }

  // always 204, so nobody can use this to check which emails have an account
  @PostMapping("forgot-password")
  @Operation(summary = "Email a reset code to the account's personal email, 204 even if there's no account")
  public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO forgotRequest) {
    userService.requestPasswordReset(forgotRequest.email());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("reset-password")
  @Operation(summary = "Set a new password with the emailed reset code")
  @ErrorCodes({ ErrorCode.RESET_CODE_INVALID, ErrorCode.PASSWORD_TOO_LONG })
  public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO resetRequest) {
    userService.resetPassword(resetRequest);
    return ResponseEntity.noContent().build();
  }

}
