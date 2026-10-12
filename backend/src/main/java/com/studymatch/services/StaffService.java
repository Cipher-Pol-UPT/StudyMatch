package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.StaffRepository;

@Service
public class StaffService {

  private final StaffRepository staffRepository;

  public StaffService(StaffRepository staffRepository) {
    this.staffRepository = staffRepository;
  }

}
