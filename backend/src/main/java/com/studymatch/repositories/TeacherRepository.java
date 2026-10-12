package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Teacher;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

}
