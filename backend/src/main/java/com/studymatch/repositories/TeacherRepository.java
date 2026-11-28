package com.studymatch.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.studymatch.models.Teacher;

import jakarta.persistence.LockModeType;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

  List<Teacher> findAllByOrderByNameAsc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Teacher> findLockedByNumber(Long number);

}
