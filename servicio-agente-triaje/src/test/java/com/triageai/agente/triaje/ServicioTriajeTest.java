package com.triageai.agente.triaje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.triageai.agente.config.LlmProperties;
import com.triageai.agente.ia.LlamadaResiliente;
import com.triageai.agente.ia.RespuestaIa;
import com.triageai.agente.ia.ResultadoTriaje;
import com.triageai.agente.tickets.ClienteTickets;
import com.triageai.agente.tickets.RegistroTriaje;
import com.triageai.agente.tickets.TicketDto;

/** Orchestration: IA result, rule-based fallback, cache, idempotency. */
class ServicioTriajeTest {

    private final UUID id = UUID.randomUUID();
    private final TicketDto ticket = new TicketDto(id, UUID.randomUUID(), "Double charge", "Charged twice", "NUEVO", 0);
    private final ResultadoTriaje resultado = new ResultadoTriaje(ResultadoTriaje.Categoria.FACTURACION,
            ResultadoTriaje.Prioridad.ALTA, "Double charge", 0.93, "Hello");

    private final ClienteTickets tickets = mock(ClienteTickets.class);
    private final LlamadaResiliente llamada = mock(LlamadaResiliente.class);
    private final CacheTriaje cache = mock(CacheTriaje.class);
    private final LlamadaLlmRepository llamadas = mock(LlamadaLlmRepository.class);
    private final ServicioTriaje servicio = new ServicioTriaje(tickets, llamada, cache, llamadas,
            new LlmProperties(null, "claude-opus-5-5", null, null, null, null),
            Clock.fixed(Instant.parse("2026-10-13T14:05:00Z"), ZoneOffset.UTC));

    private RegistroTriaje registroEnviado() {
        ArgumentCaptor<RegistroTriaje> captor = ArgumentCaptor.forClass(RegistroTriaje.class);
        verify(tickets).registrarTriaje(eq(id), captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsAiTriageWithTraceability() {
        when(tickets.obtener(id)).thenReturn(ticket);
        when(cache.buscar(ticket)).thenReturn(Optional.empty());
        when(llamada.llamar(ticket)).thenReturn(
                new LlamadaResiliente.Resultado(new RespuestaIa(resultado, 812, 164), 6430, 1, null));

        assertThat(servicio.triar(id)).isEqualTo(ServicioTriaje.Resultado.IA);

        RegistroTriaje r = registroEnviado();
        assertThat(r.modo()).isEqualTo("IA");
        assertThat(r.categoria()).isEqualTo("FACTURACION");
        assertThat(r.confianza()).isEqualTo(0.93);
        assertThat(r.trazabilidad()).isEqualTo(new RegistroTriaje.Trazabilidad(
                "ANTHROPIC", "claude-opus-5-5", 812, 164, 6430L, 1));
        verify(cache).guardar(ticket, resultado);
        verify(llamadas).save(any(LlamadaLlm.class));
    }

    @Test
    void fallsBackToRulesWhenTheAiFails() {
        when(tickets.obtener(id)).thenReturn(ticket);
        when(cache.buscar(ticket)).thenReturn(Optional.empty());
        when(llamada.llamar(ticket)).thenReturn(
                new LlamadaResiliente.Resultado(null, 9000, 3, "ANTHROPIC: HTTP 429 (circuit OPEN)"));

        assertThat(servicio.triar(id)).isEqualTo(ServicioTriaje.Resultado.REGLAS);

        RegistroTriaje r = registroEnviado();
        assertThat(r.modo()).isEqualTo("REGLAS");
        assertThat(r.motivoFallo()).isEqualTo("ANTHROPIC: HTTP 429 (circuit OPEN)");
        assertThat(r.trazabilidad().proveedor()).isEqualTo("NINGUNO");
        assertThat(r.trazabilidad().intentos()).isEqualTo(3);
        verify(cache, never()).guardar(any(), any());
    }

    @Test
    void reusesCachedTriageWithoutCallingTheLlm() {
        when(tickets.obtener(id)).thenReturn(ticket);
        when(cache.buscar(ticket)).thenReturn(Optional.of(resultado));

        assertThat(servicio.triar(id)).isEqualTo(ServicioTriaje.Resultado.IA_CACHE);

        assertThat(registroEnviado().modo()).isEqualTo("IA");
        verify(llamada, never()).llamar(any());
    }

    @Test
    void skipsTicketsThatAreAlreadyTriaged() {
        when(tickets.obtener(id)).thenReturn(new TicketDto(id, ticket.clienteId(), "t", "d", "TRIADO", 1));

        assertThat(servicio.triar(id)).isEqualTo(ServicioTriaje.Resultado.OMITIDO);

        verify(llamada, never()).llamar(any());
        verify(tickets, never()).registrarTriaje(any(), any());
    }
}
