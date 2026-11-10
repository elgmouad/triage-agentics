package com.triageai.tickets.web.dto;

import java.time.Instant;
import java.util.UUID;

import com.triageai.tickets.dominio.EstadoTicket;

public record RespuestaCreadaResponse(UUID respuestaId, UUID ticketId, EstadoTicket estado, Instant enviadaEn) {
}
