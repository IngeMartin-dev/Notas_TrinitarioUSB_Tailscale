package com.notastrinitario.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotasTrinitarioApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotasTrinitarioApplication.class, args);
	}

}
