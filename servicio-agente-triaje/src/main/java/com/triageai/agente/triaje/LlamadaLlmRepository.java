package com.triageai.agente.triaje;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LlamadaLlmRepository extends JpaRepository<LlamadaLlm, UUID> {

    /** Aggregated AI consumption over a period (CU-08). */
    interface Resumen {
        long getLlamadas();
        Long getFallos();
        Long getTokensEntrada();
        Long getTokensSalida();
        Double getLatenciaMediaMs();
    }

    @Query("""
            select count(l) as llamadas,
                   sum(case when l.exito = false then 1 else 0 end) as fallos,
                   sum(l.tokensEntrada) as tokensEntrada,
                   sum(l.tokensSalida) as tokensSalida,
                   avg(case when l.exito = true and l.desdeCache = false then l.latenciaMs end) as latenciaMediaMs
            from LlamadaLlm l
            where l.creadoEn >= :desde and l.creadoEn < :hasta""")
    Resumen resumir(Instant desde, Instant hasta);
}
