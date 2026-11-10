package com.triageai.agente.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.triageai.agente.tickets.TicketDto;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/** RN-06 / HU-04 — retry and circuit breaker around the LLM call. */
class LlamadaResilienteTest {

    private static final TicketDto TICKET = new TicketDto(UUID.randomUUID(), UUID.randomUUID(),
            "Login fails", "Error 500 after login", "NUEVO", 0);
    private static final RespuestaIa OK = new RespuestaIa(new ResultadoTriaje(ResultadoTriaje.Categoria.CUENTA,
            ResultadoTriaje.Prioridad.ALTA, "Login error", 0.9, "Hello"), 800, 150);

    private AgenteIa agente;
    private LlamadaResiliente llamada;

    @BeforeEach
    void setUp() {
        agente = mock(AgenteIa.class);
        CircuitBreakerRegistry circuitos = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1)).build());
        RetryRegistry reintentos = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(1)).build());
        llamada = new LlamadaResiliente(agente, circuitos, reintentos);
    }

    private static UnauthorizedException http401() {
        return UnauthorizedException.builder().headers(Headers.builder().build())
                .body(JsonValue.from(Map.of())).build();
    }

    private static RateLimitException http429() {
        return RateLimitException.builder().headers(Headers.builder().build())
                .body(JsonValue.from(Map.of())).build();
    }

    private static InternalServerException http500() {
        return InternalServerException.builder().statusCode(500).headers(Headers.builder().build())
                .body(JsonValue.from(Map.of())).build();
    }

    @Test
    void successOnFirstAttempt() {
        when(agente.triar(any())).thenReturn(OK);

        LlamadaResiliente.Resultado r = llamada.llamar(TICKET);

        assertThat(r.exito()).isTrue();
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.respuesta()).isEqualTo(OK);
    }

    @Test
    void retriesTransientErrorsThenSucceeds() {
        when(agente.triar(any())).thenThrow(http429()).thenThrow(http500()).thenReturn(OK);

        LlamadaResiliente.Resultado r = llamada.llamar(TICKET);

        assertThat(r.exito()).isTrue();
        assertThat(r.intentos()).isEqualTo(3);
    }

    @Test
    void invalidAnswerIsRetriedThenReportedAsFailure() {
        when(agente.triar(any())).thenThrow(new RespuestaInvalidaException("not JSON"));

        LlamadaResiliente.Resultado r = llamada.llamar(TICKET);

        assertThat(r.exito()).isFalse();
        assertThat(r.intentos()).isEqualTo(3);
        assertThat(r.motivoFallo()).startsWith("ANTHROPIC: invalid answer");
    }

    @Test
    void unauthorizedIsNotRetriedAndOpensTheCircuit() {
        when(agente.triar(any())).thenThrow(http401());

        LlamadaResiliente.Resultado r = llamada.llamar(TICKET);

        assertThat(r.exito()).isFalse();
        assertThat(r.intentos()).isEqualTo(1);
        assertThat(r.motivoFallo()).isEqualTo("ANTHROPIC: HTTP 401 (circuit OPEN)");
        assertThat(llamada.circuito().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    void openCircuitSkipsTheCall() {
        llamada.circuito().transitionToOpenState();

        LlamadaResiliente.Resultado r = llamada.llamar(TICKET);

        assertThat(r.exito()).isFalse();
        assertThat(r.motivoFallo()).isEqualTo("ANTHROPIC: circuit OPEN, call not attempted");
        verify(agente, never()).triar(any());
    }

    @Test
    void repeatedFailuresOpenTheCircuit() {
        when(agente.triar(any())).thenThrow(http500());

        llamada.llamar(TICKET); // 3 attempts recorded
        LlamadaResiliente.Resultado r = llamada.llamar(TICKET); // 4th attempt opens it

        assertThat(llamada.circuito().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(r.motivoFallo()).contains("circuit OPEN");
        verify(agente, times(4)).triar(any());
    }
}
