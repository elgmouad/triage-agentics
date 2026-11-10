package com.triageai.tickets.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /tickets (CU-01). */
public record CrearTicketRequest(
        @NotNull UUID clienteId,
        @NotBlank @Size(min = 5, max = 150) String titulo,
        @NotBlank @Size(min = 10, max = 5000) String descripcion) {
}
