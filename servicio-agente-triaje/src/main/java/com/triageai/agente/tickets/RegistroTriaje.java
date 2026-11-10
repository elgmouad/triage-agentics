package com.triageai.agente.tickets;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.triageai.agente.ia.ResultadoTriaje;

/** Body of PUT /internal/tickets/{id}/triaje (README section 7.4). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistroTriaje(
        String modo,
        String categoria,
        String prioridad,
        Double confianza,
        String resumen,
        String borradorRespuesta,
        String motivoFallo,
        Trazabilidad trazabilidad) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Trazabilidad(
            String proveedor,
            String modelo,
            Integer tokensEntrada,
            Integer tokensSalida,
            Long latenciaMs,
            Integer intentos) {
    }

    public static RegistroTriaje ia(ResultadoTriaje r, Trazabilidad traza) {
        return new RegistroTriaje("IA", r.categoria().name(), r.prioridad().name(), r.confianza(),
                r.resumen(), r.borradorRespuesta(), null, traza);
    }

    public static RegistroTriaje reglas(String motivoFallo, int intentos) {
        return new RegistroTriaje("REGLAS", null, null, null, null, null, motivoFallo,
                new Trazabilidad("NINGUNO", null, null, null, null, intentos));
    }
}
