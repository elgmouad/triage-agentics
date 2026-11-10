package com.triageai.tickets.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Aggregate root of the ticket domain. Every state change goes through
 * {@link #cambiarEstado}, which enforces RN-01 and records the history.
 */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    private UUID id;

    private UUID clienteId;
    private String titulo;
    private String descripcion;

    @Enumerated(EnumType.STRING)
    private EstadoTicket estado;

    @Enumerated(EnumType.STRING)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    private Prioridad prioridad;

    private boolean escalado;
    private boolean revisionManual;
    private int intentosTriaje;

    private Instant primeraRespuestaAntes;
    private Instant resolucionAntes;
    private Instant primeraRespuestaEn;
    private Instant resueltoEn;
    private Instant creadoEn;
    private Instant actualizadoEn;

    @Version
    private Long version;

    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL)
    private Triaje triaje;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    @OrderBy("fecha ASC")
    private List<HistorialEstado> historial = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
    @OrderBy("enviadaEn ASC")
    private List<Respuesta> respuestas = new ArrayList<>();

    protected Ticket() {
        // JPA
    }

    public Ticket(UUID clienteId, String titulo, String descripcion, Instant ahora) {
        this.id = UUID.randomUUID();
        this.clienteId = clienteId;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.estado = EstadoTicket.NUEVO;
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
        this.historial.add(new HistorialEstado(this, null, EstadoTicket.NUEVO, "Ticket creado", ahora));
    }

    /** RN-01: moves to {@code destino} or throws if the transition is not allowed. */
    public void cambiarEstado(EstadoTicket destino, String comentario, Instant ahora) {
        if (!estado.puedePasarA(destino)) {
            throw new TransicionInvalidaException(estado, destino);
        }
        historial.add(new HistorialEstado(this, estado, destino, comentario, ahora));
        if (destino == EstadoTicket.RESUELTO) {
            resueltoEn = ahora;
        } else if (estado == EstadoTicket.RESUELTO && destino == EstadoTicket.EN_CURSO) {
            resueltoEn = null; // reopened: the resolution clock restarts
        }
        estado = destino;
        actualizadoEn = ahora;
    }

    /** RN-02: deadlines are always measured from the creation date. */
    public void clasificar(Categoria categoria, Prioridad prioridad, Duration primeraRespuesta,
                           Duration resolucion, Instant ahora) {
        this.categoria = categoria;
        this.prioridad = prioridad;
        aplicarPlazos(primeraRespuesta, resolucion);
        this.actualizadoEn = ahora;
    }

    public void aplicarPlazos(Duration primeraRespuesta, Duration resolucion) {
        this.primeraRespuestaAntes = creadoEn.plus(primeraRespuesta);
        this.resolucionAntes = creadoEn.plus(resolucion);
    }

    public Triaje obtenerOCrearTriaje(Instant ahora) {
        if (triaje == null) {
            triaje = new Triaje(this, ahora);
        }
        return triaje;
    }

    public Respuesta responder(UUID agenteId, String texto, boolean basadoEnBorrador, Instant ahora) {
        Respuesta respuesta = new Respuesta(this, agenteId, texto, basadoEnBorrador, ahora);
        respuestas.add(respuesta);
        if (primeraRespuestaEn == null) {
            primeraRespuestaEn = ahora; // stops the "first response" SLA clock
        }
        actualizadoEn = ahora;
        return respuesta;
    }

    public void marcarEscalado(Instant ahora) {
        this.escalado = true;
        this.actualizadoEn = ahora;
    }

    public void marcarRevisionManual(boolean revisionManual) {
        this.revisionManual = revisionManual;
    }

    public void registrarIntentoTriaje() {
        this.intentosTriaje++;
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public EstadoTicket getEstado() { return estado; }
    public Categoria getCategoria() { return categoria; }
    public Prioridad getPrioridad() { return prioridad; }
    public boolean isEscalado() { return escalado; }
    public boolean isRevisionManual() { return revisionManual; }
    public int getIntentosTriaje() { return intentosTriaje; }
    public Instant getPrimeraRespuestaAntes() { return primeraRespuestaAntes; }
    public Instant getResolucionAntes() { return resolucionAntes; }
    public Instant getPrimeraRespuestaEn() { return primeraRespuestaEn; }
    public Instant getResueltoEn() { return resueltoEn; }
    public Instant getCreadoEn() { return creadoEn; }
    public Instant getActualizadoEn() { return actualizadoEn; }
    public Triaje getTriaje() { return triaje; }
    public List<HistorialEstado> getHistorial() { return historial; }
}
