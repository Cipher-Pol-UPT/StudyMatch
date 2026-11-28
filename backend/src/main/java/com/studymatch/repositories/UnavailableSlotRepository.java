package com.studymatch.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.studymatch.models.AcademicYear;
import com.studymatch.models.UnavailableSlot;

public interface UnavailableSlotRepository extends JpaRepository<UnavailableSlot, Long> {

  // unsorted: the day is stored as text, so the database would put Friday first
  List<UnavailableSlot> findByTeacher_NumberAndAcademicYear(Long teacherNumber, AcademicYear academicYear);

  // a bulk delete runs straight away, so the new slots that follow don't hit the unique constraint
  @Modifying
  @Query("delete from UnavailableSlot s where s.teacher.number = :teacherNumber and s.academicYear = :academicYear")
  void deleteByTeacherAndYear(Long teacherNumber, AcademicYear academicYear);

}
