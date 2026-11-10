package com.triageai.tickets.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A reply sent to the customer, always validated by a human agent (RN-05). */
@Entity
@Table(name = "respuesta")
public class Respuesta {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    private UUID agenteId;
    private String texto;
    private boolean basadoEnBorrador;
    private Instant enviadaEn;

    protected Respuesta() {
        // JPA
    }

    Respuesta(Ticket ticket, UUID agenteId, String texto, boolean basadoEnBorrador, Instant enviadaEn) {
        this.id = UUID.randomUUID();
        this.ticket = ticket;
        this.agenteId = agenteId;
        this.texto = texto;
        this.basadoEnBorrador = basadoEnBorrador;
        this.enviadaEn = enviadaEn;
    }

    public UUID getId() { return id; }
    public UUID getAgenteId() { return agenteId; }
    public String getTexto() { return texto; }
    public boolean isBasadoEnBorrador() { return basadoEnBorrador; }
    public Instant getEnviadaEn() { return enviadaEn; }
}
