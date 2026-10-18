package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.User;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByInstitutionalEmail(String institutionalEmail);

  boolean existsByInstitutionalEmail(String institutionalEmail);

  boolean existsByPersonalEmail(String personalEmail);

}
