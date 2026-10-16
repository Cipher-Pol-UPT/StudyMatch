package com.studymatch.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studymatch.dtos.CurrentUserDTO;
import com.studymatch.dtos.LoginRequestDTO;
import com.studymatch.exceptions.ErrorCode;
import com.studymatch.exceptions.StudyMatchException;
import com.studymatch.models.User;
import com.studymatch.services.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users/")
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

  @GetMapping("me")
  public ResponseEntity<CurrentUserDTO> me(Authentication authentication) {
    return ResponseEntity.ok(CurrentUserDTO.from(userService.getByInstitutionalEmail(authentication.getName())));
  }

}
