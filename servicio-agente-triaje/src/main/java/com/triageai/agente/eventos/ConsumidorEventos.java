package com.triageai.agente.eventos;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.triageai.agente.config.AgenteProperties;
import com.triageai.agente.tickets.TicketNoEncontradoException;
import com.triageai.agente.triaje.ServicioTriaje;

/**
 * Redis Streams consumer of {@code ticket.creado} (consumer group "triaje").
 * An event is acknowledged (XACK) only once its triage is recorded; if
 * servicio-tickets is down it stays pending and is retried by
 * {@link #reprocesarPendientes()}, so no ticket is lost (README section 6.4).
 */
@Component
public class ConsumidorEventos implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);
    static final String TIPO = "ticket.creado";
    private static final Duration INACTIVIDAD_MINIMA = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;
    private final ServicioTriaje triaje;
    private final AgenteProperties.Consumidor config;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> contenedor;
    private volatile boolean activo;

    public ConsumidorEventos(RedisConnectionFactory conexion, StringRedisTemplate redis, ServicioTriaje triaje,
                             AgenteProperties props) {
        this.redis = redis;
        this.triaje = triaje;
        this.config = props.consumidor();
        this.contenedor = StreamMessageListenerContainer.create(conexion,
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(10)
                        .errorHandler(e -> log.warn("Redis stream read error: {}", e.getMessage()))
                        .build());
    }

    @Override
    public void start() {
        crearGrupo();
        contenedor.register(
                StreamMessageListenerContainer.StreamReadRequest
                        .builder(StreamOffset.create(config.stream(), ReadOffset.lastConsumed()))
                        .consumer(Consumer.from(config.grupo(), config.nombre()))
                        .autoAcknowledge(false)
                        .cancelOnError(e -> false)
                        .build(),
                this::procesar);
        contenedor.start();
        activo = true;
        log.info("Consuming {} as {}/{}", config.stream(), config.grupo(), config.nombre());
    }

    @Override
    public void stop() {
        contenedor.stop();
        activo = false;
    }

    @Override
    public boolean isRunning() {
        return activo;
    }

    /** Events delivered to this consumer but never acknowledged (crash, servicio-tickets down...). */
    @Scheduled(fixedDelayString = "${agente.consumidor.pendientes-ms:60000}", initialDelay = 10_000)
    public void reprocesarPendientes() {
        if (!activo) {
            return;
        }
        PendingMessages pendientes = redis.opsForStream().pending(config.stream(),
                Consumer.from(config.grupo(), config.nombre()), Range.unbounded(), 20);
        for (PendingMessage pendiente : pendientes) {
            // Skip events that may still be in progress (an LLM call can take ~1 min)
            if (pendiente.getElapsedTimeSinceLastDelivery().compareTo(INACTIVIDAD_MINIMA) < 0) {
                continue;
            }
            String id = pendiente.getIdAsString();
            List<MapRecord<String, Object, Object>> eventos = redis.opsForStream()
                    .range(config.stream(), Range.closed(id, id));
            if (eventos == null || eventos.isEmpty()) {
                confirmar(id); // trimmed from the stream: nothing left to process
                continue;
            }
            Map<Object, Object> campos = eventos.getFirst().getValue();
            log.info("Reprocessing pending event {}", id);
            procesar(id, String.valueOf(campos.get("tipo")), String.valueOf(campos.get("ticketId")));
        }
    }

    void procesar(MapRecord<String, String, String> evento) {
        procesar(evento.getId().getValue(), evento.getValue().get("tipo"), evento.getValue().get("ticketId"));
    }

    private void procesar(String idEvento, String tipo, String ticketId) {
        try {
            if (TIPO.equals(tipo)) {
                triaje.triar(UUID.fromString(ticketId));
            } else {
                log.warn("Ignoring event {} of unknown type {}", idEvento, tipo);
            }
            confirmar(idEvento);
        } catch (TicketNoEncontradoException | IllegalArgumentException e) {
            log.warn("Discarding event {}: {}", idEvento, e.getMessage());
            confirmar(idEvento);
        } catch (RuntimeException e) {
            // Left pending: retried by reprocesarPendientes()
            log.error("Event {} for ticket {} not processed, will retry: {}", idEvento, ticketId, e.toString());
        }
    }

    private void confirmar(String idEvento) {
        redis.opsForStream().acknowledge(config.stream(), config.grupo(), idEvento);
    }

    private void crearGrupo() {
        try {
            redis.opsForStream().createGroup(config.stream(), ReadOffset.from("0"), config.grupo());
            log.info("Created consumer group {} on {}", config.grupo(), config.stream());
        } catch (RedisSystemException e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                Throwable causa = e.getMostSpecificCause();
                if (causa.getMessage() == null || !causa.getMessage().contains("BUSYGROUP")) {
                    throw e;
                }
            }
        }
    }
}
