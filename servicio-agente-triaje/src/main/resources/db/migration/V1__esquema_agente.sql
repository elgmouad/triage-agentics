-- TriageAI - servicio-agente-triaje schema (README section 6.7)
-- One row per LLM triage attempt: RN-07 traceability and AI metrics.

CREATE TABLE llamada_llm (
    id             UUID PRIMARY KEY,
    ticket_id      UUID         NOT NULL,
    proveedor      VARCHAR(20)  NOT NULL,
    modelo         VARCHAR(100),
    exito          BOOLEAN      NOT NULL,
    desde_cache    BOOLEAN      NOT NULL DEFAULT FALSE,
    tokens_entrada INTEGER,
    tokens_salida  INTEGER,
    latencia_ms    BIGINT       NOT NULL,
    intentos       INTEGER      NOT NULL,
    error          VARCHAR(500),
    creado_en      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_llamada_creado ON llamada_llm (creado_en);
CREATE INDEX idx_llamada_ticket ON llamada_llm (ticket_id);
