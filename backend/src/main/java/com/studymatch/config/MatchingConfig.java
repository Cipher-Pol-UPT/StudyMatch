package com.studymatch.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.studymatch.matching.BeeColonyMatching;
import com.studymatch.matching.GroupGenerator;

// which algorithm forms the groups and how long it gets, see docs/group-matching.md for why the bee colony
@Configuration
public class MatchingConfig {

  @Bean
  public GroupGenerator groupGenerator(@Value("${studymatch.matching-seconds:3}") double seconds) {
    return new GroupGenerator(new BeeColonyMatching(), Duration.ofMillis(Math.round(seconds * 1000)));
  }

}
