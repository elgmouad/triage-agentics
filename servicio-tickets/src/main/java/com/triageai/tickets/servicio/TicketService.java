package com.triageai.tickets.servicio;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.triageai.tickets.config.TicketsProperties;
import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.ModoTriaje;
import com.triageai.tickets.dominio.OperacionNoPermitidaException;
import com.triageai.tickets.dominio.Prioridad;
import com.triageai.tickets.dominio.Proveedor;
import com.triageai.tickets.dominio.Respuesta;
import com.triageai.tickets.dominio.Ticket;
import com.triageai.tickets.dominio.TicketNoEncontradoException;
import com.triageai.tickets.dominio.TransicionInvalidaException;
import com.triageai.tickets.dominio.Triaje;
import com.triageai.tickets.eventos.TicketCreado;
import com.triageai.tickets.repositorio.TicketRepository;
import com.triageai.tickets.web.dto.CambioEstadoResponse;
import com.triageai.tickets.web.dto.CambiarEstadoRequest;
import com.triageai.tickets.web.dto.CorregirTriajeRequest;
import com.triageai.tickets.web.dto.CrearTicketRequest;
import com.triageai.tickets.web.dto.EnviarRespuestaRequest;
import com.triageai.tickets.web.dto.HistorialClienteResponse;
import com.triageai.tickets.web.dto.PaginaResponse;
import com.triageai.tickets.web.dto.RegistrarTriajeRequest;
import com.triageai.tickets.web.dto.RespuestaCreadaResponse;
import com.triageai.tickets.web.dto.TicketCreadoResponse;
import com.triageai.tickets.web.dto.TicketResponse;

/** Use cases of servicio-tickets (CU-01, CU-02, CU-04, CU-05, CU-09 and the agent's internal API). */
@Service
@Transactional
public class TicketService {

    private final TicketRepository tickets;
    private final TicketsProperties props;
    private final ClasificadorReglas clasificador;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public TicketService(TicketRepository tickets, TicketsProperties props, ClasificadorReglas clasificador,
                         ApplicationEventPublisher eventos, Clock clock) {
        this.tickets = tickets;
        this.props = props;
        this.clasificador = clasificador;
        this.eventos = eventos;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- public API

    /** CU-01: stores the ticket as NUEVO and publishes ticket.creado after commit. */
    public TicketCreadoResponse crear(CrearTicketRequest req) {
        Instant ahora = clock.instant();
        Ticket ticket = new Ticket(req.clienteId(), req.titulo().strip(), req.descripcion().strip(), ahora);
        // Provisional SLA until the triage sets the real priority.
        TicketsProperties.Plazos plazos = props.plazos(props.getPrioridadProvisional());
        ticket.aplicarPlazos(plazos.getPrimeraRespuesta(), plazos.getResolucion());
        tickets.save(ticket);
        eventos.publishEvent(new TicketCreado(UUID.randomUUID(), ticket.getId(), ahora));
        return new TicketCreadoResponse(ticket.getId(), ticket.getEstado(), ticket.getCreadoEn());
    }

    @Transactional(readOnly = true)
    public TicketResponse obtener(UUID id) {
        return TicketResponse.de(buscar(id));
    }

    @Transactional(readOnly = true)
    public PaginaResponse<TicketResponse> listar(EstadoTicket estado, Prioridad prioridad, Categoria categoria,
                                                 Boolean escalado, Boolean revisionManual, UUID clienteId,
                                                 int pagina, int tamano) {
        Specification<Ticket> filtro = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (estado != null) condiciones.add(cb.equal(root.get("estado"), estado));
            if (prioridad != null) condiciones.add(cb.equal(root.get("prioridad"), prioridad));
            if (categoria != null) condiciones.add(cb.equal(root.get("categoria"), categoria));
            if (escalado != null) condiciones.add(cb.equal(root.get("escalado"), escalado));
            if (revisionManual != null) condiciones.add(cb.equal(root.get("revisionManual"), revisionManual));
            if (clienteId != null) condiciones.add(cb.equal(root.get("clienteId"), clienteId));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        PageRequest page = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, 100),
                Sort.by(Sort.Direction.DESC, "creadoEn"));
        return PaginaResponse.de(tickets.findAll(filtro, page).map(TicketResponse::de));
    }

    @Transactional(readOnly = true)
    public List<CambioEstadoResponse> historial(UUID id) {
        return buscar(id).getHistorial().stream().map(CambioEstadoResponse::de).toList();
    }

    /** CU-04: a support agent corrects the proposed category/priority; SLA is recomputed. */
    public TicketResponse corregirTriaje(UUID id, CorregirTriajeRequest req) {
        Ticket ticket = buscar(id);
        EstadoTicket estado = ticket.getEstado();
        if (ticket.getTriaje() == null || !(estado == EstadoTicket.PENDIENTE_TRIAJE
                || estado == EstadoTicket.TRIADO || estado == EstadoTicket.EN_CURSO)) {
            throw new OperacionNoPermitidaException("The triage of a " + estado + " ticket cannot be corrected.");
        }
        Instant ahora = clock.instant();
        Categoria categoria = req.categoria() != null ? req.categoria() : ticket.getCategoria();
        Prioridad prioridad = req.prioridad() != null ? req.prioridad() : ticket.getPrioridad();
        clasificar(ticket, categoria, prioridad, ahora);
        ticket.getTriaje().corregir(req.motivo());
        ticket.marcarRevisionManual(false);
        if (estado == EstadoTicket.PENDIENTE_TRIAJE) {
            ticket.cambiarEstado(EstadoTicket.TRIADO, "Triage completed by a support agent", ahora);
        }
        return TicketResponse.de(ticket);
    }

    /** CU-04 / RN-05: only a human-validated reply reaches the customer. */
    public RespuestaCreadaResponse responder(UUID id, EnviarRespuestaRequest req) {
        Ticket ticket = buscar(id);
        if (ticket.getEstado() != EstadoTicket.TRIADO && ticket.getEstado() != EstadoTicket.EN_CURSO) {
            throw new OperacionNoPermitidaException(
                    "A reply can only be sent to a TRIADO or EN_CURSO ticket (current: " + ticket.getEstado() + ").");
        }
        Instant ahora = clock.instant();
        Respuesta respuesta = ticket.responder(req.agenteId(), req.texto(), req.usaBorrador(), ahora);
        if (ticket.getEstado() == EstadoTicket.TRIADO) {
            ticket.cambiarEstado(EstadoTicket.EN_CURSO, "First reply sent to the customer", ahora);
        }
        return new RespuestaCreadaResponse(respuesta.getId(), ticket.getId(), ticket.getEstado(), respuesta.getEnviadaEn());
    }

    /** CU-05: manual state change, validated against RN-01. */
    public TicketResponse cambiarEstado(UUID id, CambiarEstadoRequest req) {
        Ticket ticket = buscar(id);
        EstadoTicket destino = req.estado();
        if (destino.reservadoAlTriaje() && ticket.getEstado().puedePasarA(destino)) {
            throw new TransicionInvalidaException(ticket.getEstado(),
                    destino + " can only be reached through the triage process.");
        }
        if (ticket.getEstado() == EstadoTicket.RESUELTO && destino == EstadoTicket.EN_CURSO) {
            comprobarPlazoReapertura(ticket);
        }
        ticket.cambiarEstado(destino, req.comentario(), clock.instant());
        return TicketResponse.de(ticket);
    }

    /** CU-09: the customer reopens a RESUELTO ticket within 7 days. */
    public TicketResponse reabrir(UUID id) {
        Ticket ticket = buscar(id);
        if (ticket.getEstado() != EstadoTicket.RESUELTO) {
            throw new TransicionInvalidaException(ticket.getEstado(), EstadoTicket.EN_CURSO);
        }
        comprobarPlazoReapertura(ticket);
        ticket.cambiarEstado(EstadoTicket.EN_CURSO, "Reopened by the customer", clock.instant());
        return TicketResponse.de(ticket);
    }

    // -------------------------------------------------------------- internal API

    @Transactional(readOnly = true)
    public HistorialClienteResponse historialCliente(UUID clienteId) {
        return new HistorialClienteResponse(clienteId, tickets.countByClienteId(clienteId),
                tickets.findTop5ByClienteIdOrderByCreadoEnDesc(clienteId).stream()
                        .map(HistorialClienteResponse.TicketResumen::de).toList());
    }

    /**
     * Records the agent's result. IA → TRIADO (RN-04 manual review below the threshold).
     * REGLAS → keyword-based triage and PENDIENTE_TRIAJE (RN-06).
     */
    public TicketResponse registrarTriaje(UUID id, RegistrarTriajeRequest req) {
        Ticket ticket = buscar(id);
        if (!ticket.getEstado().esperaTriaje()) {
            throw new OperacionNoPermitidaException("Ticket is already triaged (current: " + ticket.getEstado() + ").");
        }
        Instant ahora = clock.instant();
        ticket.registrarIntentoTriaje();
        Triaje triaje = ticket.obtenerOCrearTriaje(ahora);
        RegistrarTriajeRequest.Trazabilidad traza = req.trazabilidad();

        if (req.modo() == ModoTriaje.IA) {
            clasificar(ticket, req.categoria(), req.prioridad(), ahora);
            triaje.registrarIa(req.confianza(), req.resumen(), req.borradorRespuesta(), ahora);
            triaje.registrarTrazabilidad(
                    traza != null && traza.proveedor() != null ? traza.proveedor() : Proveedor.ANTHROPIC,
                    traza == null ? null : traza.modelo(),
                    traza == null ? null : traza.tokensEntrada(),
                    traza == null ? null : traza.tokensSalida(),
                    traza == null ? null : traza.latenciaMs(),
                    traza == null ? null : traza.intentos());
            boolean revision = req.confianza() < props.getUmbralConfianza();
            ticket.marcarRevisionManual(revision);
            ticket.cambiarEstado(EstadoTicket.TRIADO, String.format(Locale.ROOT,
                    "AI triage (confidence %.2f)%s", req.confianza(), revision ? ", manual review required" : ""), ahora);
        } else {
            ClasificadorReglas.Clasificacion c = clasificador.clasificar(ticket.getTitulo(), ticket.getDescripcion());
            clasificar(ticket, c.categoria(), c.prioridad(), ahora);
            triaje.registrarReglas("Rule-based triage: " + c.categoria() + " / " + c.prioridad(),
                    req.motivoFallo(), ahora);
            triaje.registrarTrazabilidad(Proveedor.NINGUNO, null, null, null, null,
                    traza == null ? null : traza.intentos());
            ticket.marcarRevisionManual(true);
            if (ticket.getEstado() == EstadoTicket.NUEVO) {
                ticket.cambiarEstado(EstadoTicket.PENDIENTE_TRIAJE,
                        "AI unavailable, rule-based triage applied: " + req.motivoFallo(), ahora);
            }
        }
        return TicketResponse.de(ticket);
    }

    // ----------------------------------------------------------------- helpers

    private Ticket buscar(UUID id) {
        return tickets.findById(id).orElseThrow(() -> new TicketNoEncontradoException(id));
    }

    private void clasificar(Ticket ticket, Categoria categoria, Prioridad prioridad, Instant ahora) {
        TicketsProperties.Plazos plazos = props.plazos(prioridad);
        ticket.clasificar(categoria, prioridad, plazos.getPrimeraRespuesta(), plazos.getResolucion(), ahora);
    }

    private void comprobarPlazoReapertura(Ticket ticket) {
        Duration plazo = Duration.ofDays(props.getCierreAutomatico().getDias());
        if (ticket.getResueltoEn() != null && clock.instant().isAfter(ticket.getResueltoEn().plus(plazo))) {
            throw new OperacionNoPermitidaException("The reopening period of "
                    + plazo.toDays() + " days has expired.");
        }
    }
}
