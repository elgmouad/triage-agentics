package com.triageai.agente.ia;

import java.util.Map;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.triageai.agente.tickets.ClienteTickets;

/** Tools the model may call during triage (tool calling). */
@Component
public class HerramientasTriaje {

    private final ClienteTickets tickets;

    public HerramientasTriaje(ClienteTickets tickets) {
        this.tickets = tickets;
    }

    @Tool(name = "obtenerHistorialCliente", description = """
            Returns the customer's total number of tickets and their 5 most recent tickets \
            (title, category, priority, state, creation date). Use it when previous tickets could \
            change the priority or the reply, for example a problem that keeps coming back.""")
    public Map<String, Object> obtenerHistorialCliente(
            @ToolParam(description = "Customer identifier (UUID) shown in the ticket") String clienteId) {
        try {
            return tickets.historialCliente(UUID.fromString(clienteId.strip()));
        } catch (IllegalArgumentException e) {
            return Map.of("error", "clienteId must be a UUID");
        }
    }
}
