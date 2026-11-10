package com.triageai.tickets.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.Prioridad;

/** PATCH /tickets/{id}/triaje (CU-04). */
public record CorregirTriajeRequest(Categoria categoria, Prioridad prioridad, @Size(max = 500) String motivo) {

    @AssertTrue(message = "categoria or prioridad is required")
    public boolean isAlgunCambio() {
        return categoria != null || prioridad != null;
    }
}
