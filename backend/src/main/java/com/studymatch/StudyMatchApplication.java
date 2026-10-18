package com.studymatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class StudyMatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(StudyMatchApplication.class, args);
	}

}
