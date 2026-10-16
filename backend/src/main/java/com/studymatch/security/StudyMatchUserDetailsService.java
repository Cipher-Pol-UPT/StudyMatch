package com.studymatch.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.studymatch.models.User;
import com.studymatch.services.UserService;

@Service
public class StudyMatchUserDetailsService implements UserDetailsService {

  private final UserService userService;

  public StudyMatchUserDetailsService(UserService userService) {
    this.userService = userService;
  }

  @Override
  public UserDetails loadUserByUsername(String institutionalEmail) throws UsernameNotFoundException {
    User user = userService.findByInstitutionalEmail(institutionalEmail)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    return org.springframework.security.core.userdetails.User.withUsername(user.getInstitutionalEmail())
        .password(user.getPassword())
        .roles(user.getRole().name())
        .build();
  }

}
