package com.triageai.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * TriageAI — centralized configuration server.
 *
 * Serves one YAML file per service from the {@code config-repo/} folder
 * (see README.md §6.3). It is the first component to start: every other
 * service imports its configuration from here at startup.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
