package com.triageai.tickets.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /tickets/{id}/respuesta (CU-04, RN-05). */
public record EnviarRespuestaRequest(
        @NotNull UUID agenteId,
        @NotBlank @Size(max = 5000) String texto,
        Boolean basadoEnBorrador) {

    /** Optional in the JSON; defaults to false. */
    public boolean usaBorrador() {
        return Boolean.TRUE.equals(basadoEnBorrador);
    }
}
