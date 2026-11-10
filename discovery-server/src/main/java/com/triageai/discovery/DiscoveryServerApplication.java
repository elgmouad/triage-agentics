package com.triageai.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * TriageAI — Eureka service registry.
 *
 * Every business and infrastructure service registers itself here at
 * startup and sends a heartbeat every 30s (see README.md §6.3).
 * api-gateway resolves {@code lb://<service-name>} through this registry.
 */
@SpringBootApplication
@EnableEurekaServer
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
