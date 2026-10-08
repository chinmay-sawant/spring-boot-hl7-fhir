package com.example.healthcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the Healthcare Interoperability Learning Platform.
 *
 * <p>Component scanning starts at {@code com.example.healthcare}, so this
 * module's classes and the {@code healthcare-fhir} client configuration are
 * picked up without extra {@code @Import} declarations.
 */
@SpringBootApplication
public class HealthcareApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthcareApplication.class, args);
    }
}
