package com.triageai.agente.ia;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.triageai.agente.tickets.TicketDto;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * RN-06 / HU-04 — wraps the LLM call in a bounded retry and a circuit breaker
 * (instance "anthropic", configured from config-server). Never throws: the
 * caller gets either a triage or the reason it failed.
 *
 * <ul>
 *   <li>Timeouts, connection errors, 429, 5xx and invalid answers are retried.</li>
 *   <li>400/401/403/404 are not retried; 401/403 open the circuit at once.</li>
 * </ul>
 */
@Component
public class LlamadaResiliente {

    public static final String INSTANCIA = "anthropic";

    private static final Logger log = LoggerFactory.getLogger(LlamadaResiliente.class);
    private static final Set<Integer> NO_TRANSITORIOS = Set.of(400, 401, 403, 404);
    private static final Set<Integer> ABREN_CIRCUITO = Set.of(401, 403);

    private final AgenteIa agente;
    private final CircuitBreaker circuito;
    private final Retry reintento;

    public LlamadaResiliente(AgenteIa agente, CircuitBreakerRegistry circuitos, RetryRegistry reintentos) {
        this.agente = agente;
        this.circuito = circuitos.circuitBreaker(INSTANCIA);
        RetryConfig base = reintentos.retry(INSTANCIA).getRetryConfig();
        this.reintento = Retry.of(INSTANCIA, RetryConfig.from(base)
                .retryOnException(LlamadaResiliente::esTransitorio)
                .build());
    }

    public record Resultado(RespuestaIa respuesta, long latenciaMs, int intentos, String motivoFallo) {
        public boolean exito() {
            return respuesta != null;
        }
    }

    public Resultado llamar(TicketDto ticket) {
        AtomicInteger intentos = new AtomicInteger();
        long inicio = System.nanoTime();
        Supplier<RespuestaIa> llamada = () -> {
            intentos.incrementAndGet();
            return agente.triar(ticket);
        };
        try {
            RespuestaIa respuesta = Retry.decorateSupplier(reintento,
                    CircuitBreaker.decorateSupplier(circuito, llamada)).get();
            return new Resultado(respuesta, msDesde(inicio), intentos.get(), null);
        } catch (CallNotPermittedException e) {
            return fallo(ticket, "ANTHROPIC: circuit OPEN, call not attempted", inicio, intentos);
        } catch (RuntimeException e) {
            Integer estado = estadoHttp(e);
            if (estado != null && ABREN_CIRCUITO.contains(estado)) {
                circuito.transitionToOpenState(); // credentials problem: stop calling the API
            }
            return fallo(ticket, describir(e) + " (circuit " + circuito.getState() + ")", inicio, intentos);
        }
    }

    public CircuitBreaker circuito() {
        return circuito;
    }

    private Resultado fallo(TicketDto ticket, String motivo, long inicio, AtomicInteger intentos) {
        log.warn("AI triage failed for ticket {} after {} attempt(s): {}", ticket.id(), intentos.get(), motivo);
        return new Resultado(null, msDesde(inicio), intentos.get(), motivo);
    }

    static boolean esTransitorio(Throwable e) {
        if (e instanceof CallNotPermittedException) {
            return false;
        }
        Integer estado = estadoHttp(e);
        return estado == null || !NO_TRANSITORIOS.contains(estado);
    }

    /** HTTP status of the Anthropic error behind {@code e}, if any. */
    static Integer estadoHttp(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AnthropicServiceException s) {
                return s.statusCode();
            }
        }
        return null;
    }

    static String describir(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof AnthropicServiceException s) {
                return "ANTHROPIC: HTTP " + s.statusCode();
            }
            if (t instanceof AnthropicIoException) {
                return "ANTHROPIC: connection error or timeout";
            }
            if (t instanceof RespuestaInvalidaException) {
                return "ANTHROPIC: invalid answer (" + t.getMessage() + ")";
            }
        }
        return "ANTHROPIC: " + e.getClass().getSimpleName();
    }

    private static long msDesde(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }
}
