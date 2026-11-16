package com.studymatch.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.studymatch.models.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  Optional<User> findByInstitutionalEmail(String institutionalEmail);

  boolean existsByInstitutionalEmail(String institutionalEmail);

  boolean existsByPersonalEmail(String personalEmail);

}
