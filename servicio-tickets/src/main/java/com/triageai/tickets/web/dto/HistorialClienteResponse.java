package com.triageai.tickets.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.Prioridad;
import com.triageai.tickets.dominio.Ticket;

/** GET /internal/clientes/{clienteId}/historial — the obtenerHistorialCliente tool. */
public record HistorialClienteResponse(UUID clienteId, long totalTickets, List<TicketResumen> ticketsRecientes) {

    public record TicketResumen(UUID id, String titulo, Categoria categoria, Prioridad prioridad,
                                EstadoTicket estado, Instant creadoEn) {

        public static TicketResumen de(Ticket t) {
            return new TicketResumen(t.getId(), t.getTitulo(), t.getCategoria(), t.getPrioridad(),
                    t.getEstado(), t.getCreadoEn());
        }
    }
}
