package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.UserRepository;

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

}
