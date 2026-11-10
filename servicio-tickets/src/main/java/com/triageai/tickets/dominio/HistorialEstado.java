package com.triageai.tickets.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One state change of a ticket (CU-02 history). */
@Entity
@Table(name = "historial_estado")
public class HistorialEstado {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    private EstadoTicket estadoAnterior;

    @Enumerated(EnumType.STRING)
    private EstadoTicket estadoNuevo;

    private String comentario;
    private Instant fecha;

    protected HistorialEstado() {
        // JPA
    }

    HistorialEstado(Ticket ticket, EstadoTicket estadoAnterior, EstadoTicket estadoNuevo,
                    String comentario, Instant fecha) {
        this.id = UUID.randomUUID();
        this.ticket = ticket;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public EstadoTicket getEstadoAnterior() { return estadoAnterior; }
    public EstadoTicket getEstadoNuevo() { return estadoNuevo; }
    public String getComentario() { return comentario; }
    public Instant getFecha() { return fecha; }
}
