package com.healthy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // registra classes como JwtProperties, que leem o application.properties
public class HealthyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(HealthyApiApplication.class, args);
	}

}
