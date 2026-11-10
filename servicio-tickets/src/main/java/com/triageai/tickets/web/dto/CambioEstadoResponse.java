package com.triageai.tickets.web.dto;

import java.time.Instant;

import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.HistorialEstado;

/** One entry of GET /tickets/{id}/historial. */
public record CambioEstadoResponse(Instant fecha, EstadoTicket estadoAnterior, EstadoTicket estadoNuevo,
                                   String comentario) {

    public static CambioEstadoResponse de(HistorialEstado h) {
        return new CambioEstadoResponse(h.getFecha(), h.getEstadoAnterior(), h.getEstadoNuevo(), h.getComentario());
    }
}
