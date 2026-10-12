package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Staff;

public interface StaffRepository extends JpaRepository<Staff, Long> {

}
