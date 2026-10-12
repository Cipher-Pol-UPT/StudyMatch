package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.AcademicYear;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Integer> {

}
