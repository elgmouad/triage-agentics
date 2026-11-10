package com.triageai.agente.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** LLM settings (config-repo/servicio-agente-triaje.yml, block "llm"). */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        String proveedor,
        String modelo,
        Integer timeoutSegundos,
        Double umbralConfianza,
        String esfuerzo,
        Integer maxTokens) {

    public LlmProperties {
        proveedor = proveedor == null ? "anthropic" : proveedor;
        modelo = modelo == null ? "claude-opus-5-5" : modelo;
        timeoutSegundos = timeoutSegundos == null ? 60 : timeoutSegundos;
        umbralConfianza = umbralConfianza == null ? 0.7 : umbralConfianza;
        esfuerzo = esfuerzo == null ? "low" : esfuerzo;
        maxTokens = maxTokens == null ? 16000 : maxTokens;
    }
}
