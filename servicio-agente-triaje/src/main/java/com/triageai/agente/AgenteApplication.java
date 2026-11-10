package com.triageai.agente;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TriageAI — AI triage agent: consumes ticket.creado, calls the LLM with
 * tool calling and resilience, and records the result in servicio-tickets.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AgenteApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenteApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
