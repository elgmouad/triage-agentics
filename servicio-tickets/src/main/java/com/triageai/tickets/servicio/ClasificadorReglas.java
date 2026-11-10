package com.triageai.tickets.servicio;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.Prioridad;

/**
 * RN-06 — keyword-based fallback triage, used when the AI is unavailable.
 * Deliberately simple and deterministic; the ticket is always flagged for
 * manual review and retried by the agent later.
 */
@Component
public class ClasificadorReglas {

    public record Clasificacion(Categoria categoria, Prioridad prioridad) {
    }

    private static final List<Regla<Categoria>> CATEGORIAS = List.of(
            new Regla<>(Categoria.FACTURACION, "factura", "cobro", "pago", "reembolso", "tarjeta",
                    "invoice", "billing", "charge", "refund", "payment"),
            new Regla<>(Categoria.CUENTA, "contrasena", "password", "login", "iniciar sesion",
                    "acceso", "cuenta", "usuario", "account", "sign in", "log in"),
            new Regla<>(Categoria.INCIDENTE, "error", "caida", "no funciona", "fallo", "bloqueado",
                    "500", "down", "outage", "crash", "broken", "not working"),
            new Regla<>(Categoria.PREGUNTA, "como puedo", "como se", "pregunta", "duda",
                    "how do", "how can", "question"));

    private static final List<Regla<Prioridad>> PRIORIDADES = List.of(
            new Regla<>(Prioridad.CRITICA, "urgente", "caida", "todo el equipo", "produccion",
                    "bloqueado", "urgent", "outage", "whole team", "production", "blocked"),
            new Regla<>(Prioridad.ALTA, "error", "no funciona", "fallo", "duplicad",
                    "not working", "broken", "duplicate", "crash"),
            new Regla<>(Prioridad.BAJA, "pregunta", "duda", "sugerencia", "como puedo",
                    "question", "suggestion", "how do", "how can"));

    public Clasificacion clasificar(String titulo, String descripcion) {
        String texto = normalizar(titulo + " " + descripcion);
        Categoria categoria = primeraCoincidencia(CATEGORIAS, texto, Categoria.OTRO);
        Prioridad prioridad = primeraCoincidencia(PRIORIDADES, texto, Prioridad.MEDIA);
        return new Clasificacion(categoria, prioridad);
    }

    private static <T> T primeraCoincidencia(List<Regla<T>> reglas, String texto, T porDefecto) {
        return reglas.stream()
                .filter(regla -> regla.coincide(texto))
                .map(Regla::valor)
                .findFirst()
                .orElse(porDefecto);
    }

    /** Lower case without accents, so "Contraseña" matches "contrasena". */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private record Regla<T>(T valor, String... palabras) {
        boolean coincide(String texto) {
            for (String palabra : palabras) {
                if (texto.contains(palabra)) {
                    return true;
                }
            }
            return false;
        }
    }
}
