package com.triageai.tickets.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.EstadoTicket;
import com.triageai.tickets.dominio.Prioridad;
import com.triageai.tickets.servicio.TicketService;
import com.triageai.tickets.web.dto.CambiarEstadoRequest;
import com.triageai.tickets.web.dto.CambioEstadoResponse;
import com.triageai.tickets.web.dto.CorregirTriajeRequest;
import com.triageai.tickets.web.dto.CrearTicketRequest;
import com.triageai.tickets.web.dto.EnviarRespuestaRequest;
import com.triageai.tickets.web.dto.PaginaResponse;
import com.triageai.tickets.web.dto.RespuestaCreadaResponse;
import com.triageai.tickets.web.dto.TicketCreadoResponse;
import com.triageai.tickets.web.dto.TicketResponse;

/**
 * Public API (README section 7.3). The gateway exposes it as /api/v1/tickets/**
 * and strips the /api/v1 prefix before forwarding.
 */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    /** External base path, used in the Location header. */
    static final String RUTA_PUBLICA = "/api/v1/tickets/";

    private final TicketService servicio;

    public TicketController(TicketService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ResponseEntity<TicketCreadoResponse> crear(@Valid @RequestBody CrearTicketRequest req) {
        TicketCreadoResponse creado = servicio.crear(req);
        return ResponseEntity.created(URI.create(RUTA_PUBLICA + creado.id())).body(creado);
    }

    @GetMapping
    public PaginaResponse<TicketResponse> listar(
            @RequestParam(required = false) EstadoTicket estado,
            @RequestParam(required = false) Prioridad prioridad,
            @RequestParam(required = false) Categoria categoria,
            @RequestParam(required = false) Boolean escalado,
            @RequestParam(required = false) Boolean revisionManual,
            @RequestParam(required = false) UUID clienteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return servicio.listar(estado, prioridad, categoria, escalado, revisionManual, clienteId, page, size);
    }

    @GetMapping("/{id}")
    public TicketResponse obtener(@PathVariable UUID id) {
        return servicio.obtener(id);
    }

    @GetMapping("/{id}/historial")
    public List<CambioEstadoResponse> historial(@PathVariable UUID id) {
        return servicio.historial(id);
    }

    @PatchMapping("/{id}/triaje")
    public TicketResponse corregirTriaje(@PathVariable UUID id, @Valid @RequestBody CorregirTriajeRequest req) {
        return servicio.corregirTriaje(id, req);
    }

    @PostMapping("/{id}/respuesta")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaCreadaResponse responder(@PathVariable UUID id, @Valid @RequestBody EnviarRespuestaRequest req) {
        return servicio.responder(id, req);
    }

    @PatchMapping("/{id}/estado")
    public TicketResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambiarEstadoRequest req) {
        return servicio.cambiarEstado(id, req);
    }

    @PostMapping("/{id}/reapertura")
    public TicketResponse reabrir(@PathVariable UUID id) {
        return servicio.reabrir(id);
    }
}
