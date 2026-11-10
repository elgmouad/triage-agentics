package com.triageai.agente.triaje;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.triageai.agente.config.AgenteProperties;
import com.triageai.agente.ia.ResultadoTriaje;
import com.triageai.agente.tickets.TicketDto;

import tools.jackson.databind.json.JsonMapper;

/**
 * Redis cache of triage results indexed by a fingerprint of the ticket text
 * (README section 6.7). Identical tickets, e.g. during a mass outage, reuse the
 * previous answer instead of calling the LLM again. Cache errors never block triage.
 */
@Component
public class CacheTriaje {

    private static final Logger log = LoggerFactory.getLogger(CacheTriaje.class);
    private static final String PREFIJO = "triaje:cache:";

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final AgenteProperties props;

    public CacheTriaje(StringRedisTemplate redis, JsonMapper json, AgenteProperties props) {
        this.redis = redis;
        this.json = json;
        this.props = props;
    }

    public Optional<ResultadoTriaje> buscar(TicketDto ticket) {
        try {
            String valor = redis.opsForValue().get(clave(ticket));
            return valor == null ? Optional.empty() : Optional.of(json.readValue(valor, ResultadoTriaje.class));
        } catch (RuntimeException e) {
            log.warn("Triage cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void guardar(TicketDto ticket, ResultadoTriaje resultado) {
        try {
            redis.opsForValue().set(clave(ticket), json.writeValueAsString(resultado), props.cache().ttl());
        } catch (RuntimeException e) {
            log.warn("Triage cache write failed: {}", e.getMessage());
        }
    }

    static String clave(TicketDto ticket) {
        String texto = Normalizer.normalize(ticket.titulo() + "\n" + ticket.descripcion(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return PREFIJO + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
