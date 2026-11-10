package com.triageai.tickets.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.triageai.tickets.dominio.OperacionNoPermitidaException;
import com.triageai.tickets.dominio.TicketNoEncontradoException;
import com.triageai.tickets.dominio.TransicionInvalidaException;

/**
 * Maps domain errors to RFC 9457 Problem Details (README section 7.1).
 * Validation errors (400) are handled by the inherited Spring handler.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final String TIPOS = "https://triageai.example/errores/";

    @ExceptionHandler(TicketNoEncontradoException.class)
    ProblemDetail noEncontrado(TicketNoEncontradoException e, HttpServletRequest req) {
        return problema(HttpStatus.NOT_FOUND, "ticket-no-encontrado", "Ticket not found", e.getMessage(), req);
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    ProblemDetail transicionInvalida(TransicionInvalidaException e, HttpServletRequest req) {
        ProblemDetail p = problema(HttpStatus.CONFLICT, "transicion-invalida", "Invalid state transition",
                e.getMessage(), req);
        p.setProperty("transicionesPermitidas", e.getOrigen().transicionesPermitidas());
        return p;
    }

    @ExceptionHandler(OperacionNoPermitidaException.class)
    ProblemDetail operacionNoPermitida(OperacionNoPermitidaException e, HttpServletRequest req) {
        return problema(HttpStatus.CONFLICT, "operacion-no-permitida", "Operation not allowed", e.getMessage(), req);
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, String detalle,
                                          HttpServletRequest req) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalle);
        p.setType(URI.create(TIPOS + tipo));
        p.setTitle(titulo);
        p.setInstance(URI.create(req.getRequestURI()));
        return p;
    }
}
