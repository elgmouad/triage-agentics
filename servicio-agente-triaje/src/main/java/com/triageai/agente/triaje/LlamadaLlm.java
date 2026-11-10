package com.triageai.agente.triaje;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One LLM triage attempt (RN-07): provider, model, tokens, latency, outcome. */
@Entity
@Table(name = "llamada_llm")
public class LlamadaLlm {

    @Id
    private UUID id;
    private UUID ticketId;
    private String proveedor;
    private String modelo;
    private boolean exito;
    private boolean desdeCache;
    private Integer tokensEntrada;
    private Integer tokensSalida;
    private long latenciaMs;
    private int intentos;
    private String error;
    private Instant creadoEn;

    protected LlamadaLlm() {
        // JPA
    }

    public LlamadaLlm(UUID ticketId, String proveedor, String modelo, boolean exito, boolean desdeCache,
                      Integer tokensEntrada, Integer tokensSalida, long latenciaMs, int intentos,
                      String error, Instant creadoEn) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        this.proveedor = proveedor;
        this.modelo = modelo;
        this.exito = exito;
        this.desdeCache = desdeCache;
        this.tokensEntrada = tokensEntrada;
        this.tokensSalida = tokensSalida;
        this.latenciaMs = latenciaMs;
        this.intentos = intentos;
        this.error = error == null || error.length() <= 500 ? error : error.substring(0, 500);
        this.creadoEn = creadoEn;
    }

    public UUID getTicketId() { return ticketId; }
    public boolean isExito() { return exito; }
    public boolean isDesdeCache() { return desdeCache; }
    public Integer getTokensEntrada() { return tokensEntrada; }
    public Integer getTokensSalida() { return tokensSalida; }
    public long getLatenciaMs() { return latenciaMs; }
    public int getIntentos() { return intentos; }
    public String getError() { return error; }
}
