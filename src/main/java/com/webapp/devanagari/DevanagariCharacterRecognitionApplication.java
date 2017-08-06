package com.webapp.devanagari;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.webapp.devanagari.storage.StorageProperties;
import com.webapp.devanagari.storage.StorageService;

@SpringBootApplication
@ComponentScan("com.webapp")
@EnableConfigurationProperties(StorageProperties.class)
public class DevanagariCharacterRecognitionApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevanagariCharacterRecognitionApplication.class, args);
	}
	
	@Bean
	CommandLineRunner init(StorageService storageService) {
		return (args) -> {
            storageService.init();
		};
	}
}
