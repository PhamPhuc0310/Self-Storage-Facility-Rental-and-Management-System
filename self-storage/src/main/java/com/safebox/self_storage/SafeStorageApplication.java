package com.safebox.self_storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SafeStorageApplication {

	public static void main(String[] args) {
		SpringApplication.run(SafeStorageApplication.class, args);
	}

}
