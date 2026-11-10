package com.triageai.tickets.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.triageai.tickets.servicio.TicketService;
import com.triageai.tickets.web.dto.HistorialClienteResponse;
import com.triageai.tickets.web.dto.RegistrarTriajeRequest;
import com.triageai.tickets.web.dto.TicketResponse;

/**
 * Internal API used by servicio-agente-triaje. Never routed by the gateway:
 * only reachable on the internal Docker network.
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final TicketService servicio;

    public InternalController(TicketService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/tickets/{id}")
    public TicketResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @GetMapping("/clientes/{clienteId}/historial")
    public HistorialClienteResponse historialCliente(@PathVariable UUID clienteId) {
        return servicio.historialCliente(clienteId);
    }

    @PutMapping("/tickets/{id}/triaje")
    public TicketResponse registrarTriaje(@PathVariable UUID id, @Valid @RequestBody RegistrarTriajeRequest req) {
        return servicio.registrarTriaje(id, req);
    }
}
