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

// after login: blocks blocked accounts, and holds back users who still owe a password change or the personality test
public class AccountStateFilter extends OncePerRequestFilter {

  private static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE = Set.of(
      "GET /users/me",
      "PUT /users/change-password");

  private static final Set<String> ALLOWED_BEFORE_PERSONALITY_TEST = Set.of(
      "GET /users/me",
      "PUT /users/change-password",
      "POST /students/personality-test");

  // the whole helpdesk, someone stuck on the test can still ask for help and read the answer (#362)
  private static final String HELPDESK_PATH = "/tickets/";

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

    String path = request.getRequestURI().substring(request.getContextPath().length());
    String endpoint = request.getMethod() + " " + path;
    if (user.get().isMustChangePassword() && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(endpoint)) {
      errorWriter.write(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
      return;
    }
    if (user.get().requiresPersonalityTest() && !ALLOWED_BEFORE_PERSONALITY_TEST.contains(endpoint)
        && !path.startsWith(HELPDESK_PATH)) {
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
