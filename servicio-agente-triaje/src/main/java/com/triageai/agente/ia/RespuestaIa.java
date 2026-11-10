package com.triageai.agente.ia;

/** Triage plus the token usage of the call (RN-07). */
public record RespuestaIa(ResultadoTriaje resultado, Integer tokensEntrada, Integer tokensSalida) {
}
