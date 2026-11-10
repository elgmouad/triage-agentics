package com.triageai.agente.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Agent settings (block "agente"). Defaults allow running without config-server. */
@ConfigurationProperties(prefix = "agente")
public record AgenteProperties(
        String ticketsUrl,
        Consumidor consumidor,
        Reintento reintento,
        Cache cache) {

    public AgenteProperties {
        ticketsUrl = ticketsUrl == null ? "http://servicio-tickets" : ticketsUrl;
        consumidor = consumidor == null ? new Consumidor(null, null, null) : consumidor;
        reintento = reintento == null ? new Reintento(null, null) : reintento;
        cache = cache == null ? new Cache(null) : cache;
    }

    /** Redis Streams consumer (group "triaje" on stream "ticket-events"). */
    public record Consumidor(String stream, String grupo, String nombre) {
        public Consumidor {
            stream = stream == null ? "ticket-events" : stream;
            grupo = grupo == null ? "triaje" : grupo;
            nombre = nombre == null ? "agente-local" : nombre;
        }
    }

    /** HU-08 — scheduled retry of PENDIENTE_TRIAJE tickets. */
    public record Reintento(Integer maxIntentos, Long frecuenciaMs) {
        public Reintento {
            maxIntentos = maxIntentos == null ? 5 : maxIntentos;
            frecuenciaMs = frecuenciaMs == null ? 300_000L : frecuenciaMs;
        }
    }

    /** Triage cache for identical ticket texts. */
    public record Cache(Duration ttl) {
        public Cache {
            ttl = ttl == null ? Duration.ofHours(1) : ttl;
        }
    }
}
