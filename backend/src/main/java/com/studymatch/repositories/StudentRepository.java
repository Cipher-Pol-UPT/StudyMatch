package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {

}
