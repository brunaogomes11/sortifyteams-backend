package com.gomesdev.sortifyteams;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SortifyteamsApplication {

	public static void main(String[] args) {
		SpringApplication.run(SortifyteamsApplication.class, args);
	}

}
