package com.studymatch.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studymatch.models.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

}
