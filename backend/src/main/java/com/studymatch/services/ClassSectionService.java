package com.studymatch.services;

import org.springframework.stereotype.Service;

import com.studymatch.repositories.ClassSectionRepository;

@Service
public class ClassSectionService {

  private final ClassSectionRepository classSectionRepository;

  public ClassSectionService(ClassSectionRepository classSectionRepository) {
    this.classSectionRepository = classSectionRepository;
  }

}
