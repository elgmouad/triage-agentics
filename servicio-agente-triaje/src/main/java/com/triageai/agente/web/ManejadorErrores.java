package com.triageai.agente.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.triageai.agente.tickets.TicketNoEncontradoException;

/** RFC 9457 Problem Details, same conventions as servicio-tickets. */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final String TIPOS = "https://triageai.example/errores/";

    @ExceptionHandler(TicketNoEncontradoException.class)
    ProblemDetail noEncontrado(TicketNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "ticket-no-encontrado", "Ticket not found", e.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    ProblemDetail conflicto(ConflictoException e) {
        return problema(HttpStatus.CONFLICT, "operacion-no-permitida", "Operation not allowed", e.getMessage());
    }

    @ExceptionHandler(RestClientException.class)
    ProblemDetail ticketsNoDisponible(RestClientException e) {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "servicio-no-disponible", "servicio-tickets unavailable",
                "servicio-tickets could not be reached.");
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, String detalle) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalle);
        p.setType(URI.create(TIPOS + tipo));
        p.setTitle(titulo);
        return p;
    }
}
