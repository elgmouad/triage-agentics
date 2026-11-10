package com.triageai.tickets.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.triageai.tickets.dominio.Prioridad;

/**
 * Business settings read from config-server (config-repo/servicio-tickets.yml).
 * The defaults below match the README so the service also runs without it.
 */
@ConfigurationProperties(prefix = "tickets")
public class TicketsProperties {

    /** RN-02 — SLA deadlines by priority. */
    private Map<Prioridad, Plazos> sla = new EnumMap<>(Map.of(
            Prioridad.CRITICA, new Plazos(Duration.ofHours(1), Duration.ofHours(8)),
            Prioridad.ALTA, new Plazos(Duration.ofHours(4), Duration.ofHours(24)),
            Prioridad.MEDIA, new Plazos(Duration.ofHours(24), Duration.ofDays(3)),
            Prioridad.BAJA, new Plazos(Duration.ofHours(72), Duration.ofDays(7))));

    /** Priority used for the provisional SLA until the ticket is triaged. */
    private Prioridad prioridadProvisional = Prioridad.MEDIA;

    /** RN-04 — below this AI confidence the ticket needs a manual review. */
    private double umbralConfianza = 0.7;

    private final Escalado escalado = new Escalado();
    private final CierreAutomatico cierreAutomatico = new CierreAutomatico();

    public Plazos plazos(Prioridad prioridad) {
        return sla.get(prioridad);
    }

    public static class Plazos {
        private Duration primeraRespuesta;
        private Duration resolucion;

        public Plazos() {
        }

        public Plazos(Duration primeraRespuesta, Duration resolucion) {
            this.primeraRespuesta = primeraRespuesta;
            this.resolucion = resolucion;
        }

        public Duration getPrimeraRespuesta() { return primeraRespuesta; }
        public void setPrimeraRespuesta(Duration primeraRespuesta) { this.primeraRespuesta = primeraRespuesta; }
        public Duration getResolucion() { return resolucion; }
        public void setResolucion(Duration resolucion) { this.resolucion = resolucion; }
    }

    /** RN-03 — escalate when this fraction of the deadline has elapsed. */
    public static class Escalado {
        private double umbral = 0.8;
        private long frecuenciaMs = 60_000;

        public double getUmbral() { return umbral; }
        public void setUmbral(double umbral) { this.umbral = umbral; }
        public long getFrecuenciaMs() { return frecuenciaMs; }
        public void setFrecuenciaMs(long frecuenciaMs) { this.frecuenciaMs = frecuenciaMs; }
    }

    /** RN-01 — RESUELTO tickets close automatically after this many days. */
    public static class CierreAutomatico {
        private int dias = 7;
        private long frecuenciaMs = 3_600_000;

        public int getDias() { return dias; }
        public void setDias(int dias) { this.dias = dias; }
        public long getFrecuenciaMs() { return frecuenciaMs; }
        public void setFrecuenciaMs(long frecuenciaMs) { this.frecuenciaMs = frecuenciaMs; }
    }

    public Map<Prioridad, Plazos> getSla() { return sla; }
    public void setSla(Map<Prioridad, Plazos> sla) { this.sla.putAll(sla); }
    public Prioridad getPrioridadProvisional() { return prioridadProvisional; }
    public void setPrioridadProvisional(Prioridad prioridadProvisional) { this.prioridadProvisional = prioridadProvisional; }
    public double getUmbralConfianza() { return umbralConfianza; }
    public void setUmbralConfianza(double umbralConfianza) { this.umbralConfianza = umbralConfianza; }
    public Escalado getEscalado() { return escalado; }
    public CierreAutomatico getCierreAutomatico() { return cierreAutomatico; }
}
