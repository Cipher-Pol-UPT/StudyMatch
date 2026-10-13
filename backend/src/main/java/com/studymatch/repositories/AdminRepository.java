package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Admin;

public interface AdminRepository extends JpaRepository<Admin, Long> {

}
