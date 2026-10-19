package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.AcademicYear;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Integer> {

  List<AcademicYear> findAllByOrderByStartYearDesc();

}
