package com.triageai.tickets.eventos;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Redis Streams producer. The event is written only after the database
 * transaction commits, so the agent never reads a ticket that does not exist yet.
 */
@Component
public class PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final StringRedisTemplate redis;
    private final String stream;

    public PublicadorEventos(StringRedisTemplate redis,
                             @Value("${triageai.eventos.stream:ticket-events}") String stream) {
        this.redis = redis;
        this.stream = stream;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(TicketCreado evento) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("eventoId", evento.eventoId().toString());
        campos.put("tipo", TicketCreado.TIPO);
        campos.put("version", String.valueOf(TicketCreado.VERSION));
        campos.put("ticketId", evento.ticketId().toString());
        campos.put("ocurridoEn", evento.ocurridoEn().toString());
        try {
            RecordId id = redis.opsForStream().add(StreamRecords.string(campos).withStreamKey(stream));
            log.info("Published {} for ticket {} (stream {}, id {})", TicketCreado.TIPO, evento.ticketId(), stream, id);
        } catch (RuntimeException e) {
            // The ticket is already stored; it stays NUEVO and visible to support staff.
            log.error("Could not publish {} for ticket {}", TicketCreado.TIPO, evento.ticketId(), e);
        }
    }
}
