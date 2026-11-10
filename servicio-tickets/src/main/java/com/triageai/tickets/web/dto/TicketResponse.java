package com.triageai.tickets.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.ModoTriaje;
import com.triageai.tickets.dominio.Prioridad;
import com.triageai.tickets.dominio.Proveedor;
import com.triageai.tickets.dominio.Ticket;
import com.triageai.tickets.dominio.Triaje;

/** Full ticket view (CU-02), also used by the agent through the internal API. */
public record TicketResponse(
        UUID id,
        UUID clienteId,
        String titulo,
        String descripcion,
        EstadoTicket estado,
        Categoria categoria,
        Prioridad prioridad,
        boolean escalado,
        boolean revisionManual,
        int intentosTriaje,
        Sla sla,
        TriajeVista triaje,
        Instant creadoEn,
        Instant actualizadoEn) {

    public record Sla(Instant primeraRespuestaAntes, Instant resolucionAntes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TriajeVista(
            ModoTriaje modo,
            Proveedor proveedor,
            Double confianza,
            String resumen,
            String borradorRespuesta,
            String motivoFallo,
            boolean corregidoPorHumano) {
    }

    public static TicketResponse de(Ticket t) {
        Triaje tr = t.getTriaje();
        TriajeVista triaje = tr == null ? null : new TriajeVista(tr.getModo(), tr.getProveedor(),
                tr.getConfianza(), tr.getResumen(), tr.getBorradorRespuesta(), tr.getMotivoFallo(),
                tr.isCorregidoPorHumano());
        return new TicketResponse(t.getId(), t.getClienteId(), t.getTitulo(), t.getDescripcion(),
                t.getEstado(), t.getCategoria(), t.getPrioridad(), t.isEscalado(), t.isRevisionManual(),
                t.getIntentosTriaje(), new Sla(t.getPrimeraRespuestaAntes(), t.getResolucionAntes()), triaje,
                t.getCreadoEn(), t.getActualizadoEn());
    }
}
