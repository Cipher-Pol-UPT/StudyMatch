package com.studymatch.security;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.studymatch.exceptions.ErrorCode;
import com.studymatch.models.User;
import com.studymatch.services.UserService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class AccountStateFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE = Set.of(
      "GET /users/me",
      "PUT /users/change-password");

  private static final Set<String> ALLOWED_BEFORE_PERSONALITY_TEST = Set.of(
      "GET /users/me",
      "PUT /users/change-password",
      "POST /students/personality-test");

  private final UserService userService;
  private final SecurityErrorWriter errorWriter;

  public AccountStateFilter(UserService userService, SecurityErrorWriter errorWriter) {
    this.userService = userService;
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      chain.doFilter(request, response);
      return;
    }

    Optional<User> user = userService.findByInstitutionalEmail(authentication.getName());
    if (user.isEmpty()) {
      endSession(request);
      errorWriter.write(response, ErrorCode.UNAUTHENTICATED);
      return;
    }
    if (user.get().isBlocked()) {
      endSession(request);
      errorWriter.write(response, ErrorCode.ACCOUNT_BLOCKED);
      return;
    }

    String endpoint = request.getMethod() + " " + request.getRequestURI().substring(request.getContextPath().length());
    if (user.get().isMustChangePassword() && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(endpoint)) {
      errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
      return;
    }
    if (user.get().requiresPersonalityTest() && !ALLOWED_BEFORE_PERSONALITY_TEST.contains(endpoint)) {
      errorWriter.write(response, ErrorCode.PERSONALITY_TEST_REQUIRED);
      return;
    }

    chain.doFilter(request, response);
  }

  private void endSession(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
  }

}
