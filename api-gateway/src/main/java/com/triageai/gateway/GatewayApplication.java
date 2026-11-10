package com.triageai.gateway;

import java.net.InetSocketAddress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;

import reactor.core.publisher.Mono;

/**
 * TriageAI — single entry point (port 8080). Routes /api/v1/** to the business
 * services through Eureka (lb://) and applies Redis rate limiting.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Rate-limit key: the client's IP (authentication is out of the MVP scope).
     * X-Forwarded-For is only trusted for the number of proxies configured in
     * front of the gateway (0 by default), so clients cannot forge their identity.
     */
    @Bean
    KeyResolver clientePorIp(@Value("${gateway.proxies-confianza:0}") int proxiesConfianza) {
        RemoteAddressResolver resolver = proxiesConfianza > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(proxiesConfianza)
                : new RemoteAddressResolver() { };
        return exchange -> {
            InetSocketAddress cliente = resolver.resolve(exchange);
            return Mono.just(cliente == null || cliente.getAddress() == null
                    ? "desconocido" : cliente.getAddress().getHostAddress());
        };
    }
}
