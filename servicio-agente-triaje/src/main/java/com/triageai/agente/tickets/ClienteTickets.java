package com.triageai.agente.tickets;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.triageai.agente.config.AgenteProperties;

/**
 * HTTP client for servicio-tickets' internal API. The base URL
 * (http://servicio-tickets) is resolved through Eureka by the load balancer.
 */
@Component
public class ClienteTickets {

    private static final Logger log = LoggerFactory.getLogger(ClienteTickets.class);

    private final RestClient http;

    public ClienteTickets(@LoadBalanced RestClient.Builder builder, AgenteProperties props) {
        this.http = builder.baseUrl(props.ticketsUrl()).build();
    }

    public TicketDto obtener(UUID id) {
        try {
            return http.get().uri("/internal/tickets/{id}", id).retrieve().body(TicketDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new TicketNoEncontradoException(id);
        }
    }

    /** Backs the obtenerHistorialCliente tool: returned as-is to the model. */
    public Map<String, Object> historialCliente(UUID clienteId) {
        return http.get().uri("/internal/clientes/{id}/historial", clienteId).retrieve()
                .body(new ParameterizedTypeReference<>() { });
    }

    /** Records the result. A 409 means another instance already triaged it: not an error. */
    public void registrarTriaje(UUID id, RegistroTriaje registro) {
        try {
            http.put().uri("/internal/tickets/{id}/triaje", id).body(registro).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() != HttpStatus.CONFLICT) {
                throw e;
            }
            log.info("Ticket {} was already triaged, result discarded", id);
        }
    }

    /** HU-08: tickets left in PENDIENTE_TRIAJE (public listing, oldest page first is enough). */
    public List<TicketDto> pendientesDeTriaje(int maximo) {
        Pagina pagina = http.get()
                .uri(uri -> uri.path("/tickets").queryParam("estado", "PENDIENTE_TRIAJE")
                        .queryParam("size", maximo).build())
                .retrieve().body(Pagina.class);
        return pagina == null ? List.of() : pagina.contenido();
    }

    record Pagina(List<TicketDto> contenido) {
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {

        /** Resolves service names through Eureka; only injected where asked with @LoadBalanced. */
        @Bean
        @LoadBalanced
        @Scope("prototype")
        RestClient.Builder restClientBuilderBalanceado() {
            return RestClient.builder();
        }

        /** Plain builder for every other component (e.g. calls to real host names). */
        @Bean
        @Primary
        @Scope("prototype")
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
