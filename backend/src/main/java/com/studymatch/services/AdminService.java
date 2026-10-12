package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.AdminRepository;

@Service
public class AdminService {

  private final AdminRepository adminRepository;

  public AdminService(AdminRepository adminRepository) {
    this.adminRepository = adminRepository;
  }

}
