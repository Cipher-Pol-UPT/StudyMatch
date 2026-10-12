package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.User;

public interface UserRepository extends JpaRepository<User, Long> {

}
