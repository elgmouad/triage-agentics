package com.triageai.tickets.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.ModoTriaje;
import com.triageai.tickets.dominio.Prioridad;
import com.triageai.tickets.dominio.Proveedor;

/** PUT /internal/tickets/{id}/triaje — result sent by the AI agent. */
public record RegistrarTriajeRequest(
        @NotNull ModoTriaje modo,
        Categoria categoria,
        Prioridad prioridad,
        @DecimalMin("0.0") @DecimalMax("1.0") Double confianza,
        @Size(max = 2000) String resumen,
        @Size(max = 5000) String borradorRespuesta,
        @Size(max = 500) String motivoFallo,
        @Valid Trazabilidad trazabilidad) {

    /** RN-07 — provider, tokens and latency of the call. */
    public record Trazabilidad(
            Proveedor proveedor,
            @Size(max = 100) String modelo,
            Integer tokensEntrada,
            Integer tokensSalida,
            Long latenciaMs,
            Integer intentos) {
    }

    @AssertTrue(message = "an IA triage requires categoria, prioridad and confianza")
    public boolean isIaCompleto() {
        return modo != ModoTriaje.IA || (categoria != null && prioridad != null && confianza != null);
    }
}
