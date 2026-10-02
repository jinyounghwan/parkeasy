package com.parkeasy.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ParkEasyApplication {

	public static void main(String[] args) {
		SpringApplication.run(ParkEasyApplication.class, args);
	}

}


