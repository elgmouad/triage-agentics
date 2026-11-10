-- TriageAI - servicio-tickets schema (README section 6.7)

CREATE TABLE ticket (
    id                      UUID PRIMARY KEY,
    cliente_id              UUID          NOT NULL,
    titulo                  VARCHAR(150)  NOT NULL,
    descripcion             VARCHAR(5000) NOT NULL,
    estado                  VARCHAR(20)   NOT NULL,
    categoria               VARCHAR(20),
    prioridad               VARCHAR(10),
    escalado                BOOLEAN       NOT NULL DEFAULT FALSE,
    revision_manual         BOOLEAN       NOT NULL DEFAULT FALSE,
    intentos_triaje         INTEGER       NOT NULL DEFAULT 0,
    primera_respuesta_antes TIMESTAMP WITH TIME ZONE,
    resolucion_antes        TIMESTAMP WITH TIME ZONE,
    primera_respuesta_en    TIMESTAMP WITH TIME ZONE,
    resuelto_en             TIMESTAMP WITH TIME ZONE,
    creado_en               TIMESTAMP WITH TIME ZONE NOT NULL,
    actualizado_en          TIMESTAMP WITH TIME ZONE NOT NULL,
    version                 BIGINT        NOT NULL
);

CREATE INDEX idx_ticket_cliente ON ticket (cliente_id, creado_en DESC);
CREATE INDEX idx_ticket_estado  ON ticket (estado);

CREATE TABLE triaje (
    id                   UUID PRIMARY KEY,
    ticket_id            UUID        NOT NULL UNIQUE REFERENCES ticket (id),
    modo                 VARCHAR(10) NOT NULL,
    proveedor            VARCHAR(20) NOT NULL,
    modelo               VARCHAR(100),
    confianza            DOUBLE PRECISION,
    resumen              VARCHAR(2000),
    borrador_respuesta   VARCHAR(5000),
    motivo_fallo         VARCHAR(500),
    corregido_por_humano BOOLEAN     NOT NULL DEFAULT FALSE,
    motivo_correccion    VARCHAR(500),
    tokens_entrada       INTEGER,
    tokens_salida        INTEGER,
    latencia_ms          BIGINT,
    intentos             INTEGER,
    registrado_en        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE historial_estado (
    id              UUID PRIMARY KEY,
    ticket_id       UUID        NOT NULL REFERENCES ticket (id),
    estado_anterior VARCHAR(20),
    estado_nuevo    VARCHAR(20) NOT NULL,
    comentario      VARCHAR(500),
    fecha           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_historial_ticket ON historial_estado (ticket_id, fecha);

CREATE TABLE respuesta (
    id                 UUID PRIMARY KEY,
    ticket_id          UUID          NOT NULL REFERENCES ticket (id),
    agente_id          UUID          NOT NULL,
    texto              VARCHAR(5000) NOT NULL,
    basado_en_borrador BOOLEAN       NOT NULL,
    enviada_en         TIMESTAMP WITH TIME ZONE NOT NULL
);
