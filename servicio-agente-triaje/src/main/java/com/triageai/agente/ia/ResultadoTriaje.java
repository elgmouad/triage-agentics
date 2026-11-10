package com.triageai.agente.ia;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Structured answer expected from the model. Enum fields reject any value
 * outside the contract, which counts as an invalid response (RN-06).
 */
public record ResultadoTriaje(
        @JsonPropertyDescription("Ticket category") Categoria categoria,
        @JsonPropertyDescription("Ticket priority") Prioridad prioridad,
        @JsonPropertyDescription("One-sentence summary, in the language of the ticket") String resumen,
        @JsonPropertyDescription("Confidence between 0 and 1 that categoria and prioridad are right") double confianza,
        @JsonPropertyDescription("Short draft reply for a human agent to review, in the language of the ticket")
        String borradorRespuesta) {

    public enum Categoria { INCIDENTE, FACTURACION, CUENTA, PREGUNTA, OTRO }

    public enum Prioridad { CRITICA, ALTA, MEDIA, BAJA }

    /** Rejects answers that parsed but are unusable. */
    public void validar() {
        if (categoria == null || prioridad == null || confianza < 0 || confianza > 1
                || resumen == null || resumen.isBlank()) {
            throw new RespuestaInvalidaException("Incomplete or out-of-range triage: " + this);
        }
    }
}
