package com.triageai.tickets.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.triageai.tickets.dominio.EstadoTicket;

/** PATCH /tickets/{id}/estado (CU-05). */
public record CambiarEstadoRequest(@NotNull EstadoTicket estado, @Size(max = 500) String comentario) {
}
