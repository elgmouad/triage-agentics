package com.triageai.tickets.dominio;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Latest triage of a ticket (AI or rule-based) with its traceability data (RN-07).
 * Updated in place on every new triage attempt.
 */
@Entity
@Table(name = "triaje")
public class Triaje {

    @Id
    private UUID id;

    @OneToOne
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    private ModoTriaje modo;

    @Enumerated(EnumType.STRING)
    private Proveedor proveedor;

    private String modelo;
    private Double confianza;
    private String resumen;
    private String borradorRespuesta;
    private String motivoFallo;
    private boolean corregidoPorHumano;
    private String motivoCorreccion;
    private Integer tokensEntrada;
    private Integer tokensSalida;
    private Long latenciaMs;
    private Integer intentos;
    private Instant registradoEn;

    protected Triaje() {
        // JPA
    }

    Triaje(Ticket ticket, Instant ahora) {
        this.id = UUID.randomUUID();
        this.ticket = ticket;
        this.registradoEn = ahora;
    }

    public void registrarIa(Double confianza, String resumen, String borradorRespuesta, Instant ahora) {
        this.modo = ModoTriaje.IA;
        this.confianza = confianza;
        this.resumen = resumen;
        this.borradorRespuesta = borradorRespuesta;
        this.motivoFallo = null;
        this.registradoEn = ahora;
    }

    public void registrarReglas(String resumen, String motivoFallo, Instant ahora) {
        this.modo = ModoTriaje.REGLAS;
        this.confianza = null;
        this.resumen = resumen;
        this.borradorRespuesta = null;
        this.motivoFallo = motivoFallo;
        this.registradoEn = ahora;
    }

    public void registrarTrazabilidad(Proveedor proveedor, String modelo, Integer tokensEntrada,
                                      Integer tokensSalida, Long latenciaMs, Integer intentos) {
        this.proveedor = proveedor;
        this.modelo = modelo;
        this.tokensEntrada = tokensEntrada;
        this.tokensSalida = tokensSalida;
        this.latenciaMs = latenciaMs;
        this.intentos = intentos;
    }

    public void corregir(String motivo) {
        this.corregidoPorHumano = true;
        this.motivoCorreccion = motivo;
    }

    public ModoTriaje getModo() { return modo; }
    public Proveedor getProveedor() { return proveedor; }
    public String getModelo() { return modelo; }
    public Double getConfianza() { return confianza; }
    public String getResumen() { return resumen; }
    public String getBorradorRespuesta() { return borradorRespuesta; }
    public String getMotivoFallo() { return motivoFallo; }
    public boolean isCorregidoPorHumano() { return corregidoPorHumano; }
    public String getMotivoCorreccion() { return motivoCorreccion; }
    public Integer getTokensEntrada() { return tokensEntrada; }
    public Integer getTokensSalida() { return tokensSalida; }
    public Long getLatenciaMs() { return latenciaMs; }
    public Integer getIntentos() { return intentos; }
    public Instant getRegistradoEn() { return registradoEn; }
}
