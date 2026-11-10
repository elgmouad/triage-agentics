# syntax=docker/dockerfile:1
# ============================================================
#  TriageAI — servicio-tickets
#  Multi-stage build: compile (JDK + Maven) -> extract layers -> run (JRE only)
# ============================================================

# ---------- 1. Build: JDK + Maven, discarded after the build ----------
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /src
# Dependencies first: this layer is rebuilt only when pom.xml changes.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in CI (they need Docker for Testcontainers), not in the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && cp target/*.jar /src/app.jar

# ---------- 2. Split the fat jar into layers (libraries change less than code) ----------
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /layers
COPY --from=build /src/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- 3. Runtime: minimal JRE on Alpine, non-root ----------
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="triageai-servicio-tickets" \
      org.opencontainers.image.description="TriageAI ticket lifecycle, SLA and rule-based triage" \
      org.opencontainers.image.source="https://github.com/elgmouad/triage-agentics"
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H -s /sbin/nologin app
WORKDIR /app
# Least-changing layers first to maximise cache reuse between builds.
COPY --from=layers --chown=app:app /layers/extracted/dependencies/ ./
COPY --from=layers --chown=app:app /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=app:app /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=app:app /layers/extracted/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD ["wget", "-q", "-O", "/dev/null", "http://localhost:8081/actuator/health"]
ENTRYPOINT ["java", "-jar", "app.jar"]
