package com.studymatch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// the tests hash a password for nearly every user they create, with the real strength that's most of their time.
// same bcrypt at its lowest strength, the app itself keeps the default one
@Configuration
@Profile("test")
public class FastPasswordConfig {

  @Bean
  @Primary
  public PasswordEncoder fastPasswordEncoder() {
    return new BCryptPasswordEncoder(4);
  }

}
