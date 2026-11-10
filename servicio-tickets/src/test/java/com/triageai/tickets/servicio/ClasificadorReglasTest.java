package com.triageai.tickets.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.triageai.tickets.dominio.Categoria;
import com.triageai.tickets.dominio.Prioridad;

/** RN-06 keyword-based fallback triage. */
class ClasificadorReglasTest {

    private final ClasificadorReglas clasificador = new ClasificadorReglas();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Factura duplicada        | Me habéis cobrado dos veces este mes                | FACTURACION | ALTA",
            "No puedo iniciar sesión  | Olvidé mi contraseña y no me llega el correo        | CUENTA      | MEDIA",
            "Cannot log into account  | 500 error after login. The whole team is blocked.   | CUENTA      | CRITICA",
            "Caída del servicio       | La web no funciona desde esta mañana, es urgente    | INCIDENTE   | CRITICA",
            "Pregunta sobre informes  | ¿Cómo puedo exportar los datos a CSV?               | PREGUNTA    | BAJA",
            "Hola                     | Quería comentaros algo sobre vuestro producto       | OTRO        | MEDIA"})
    void classifiesByKeywords(String titulo, String descripcion, Categoria categoria, Prioridad prioridad) {
        ClasificadorReglas.Clasificacion c = clasificador.clasificar(titulo, descripcion);

        assertThat(c.categoria()).isEqualTo(categoria);
        assertThat(c.prioridad()).isEqualTo(prioridad);
    }
}
