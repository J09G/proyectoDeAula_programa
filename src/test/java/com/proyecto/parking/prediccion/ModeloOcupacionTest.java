package com.proyecto.parking.prediccion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Modelo predictivo de ocupación")
class ModeloOcupacionTest {

    private final ModeloOcupacion modelo = new ModeloOcupacion();

    @Test
    @DisplayName("predice lo mismo que scikit-learn en los casos que exportó el entrenamiento")
    void coincideConElEntrenamiento() {
        JsonNode casos = modelo.casosDePrueba();
        assertThat(casos.size()).isGreaterThanOrEqualTo(20);

        for (JsonNode caso : casos) {
            double[] x = new double[modelo.numeroDeVariables()];
            for (int i = 0; i < x.length; i++) {
                x[i] = caso.get("entrada").get(i).asDouble();
            }
            assertThat(modelo.predecir(x)).isCloseTo(caso.get("esperado").asDouble(), within(0.05));
        }
    }

    @Test
    @DisplayName("la predicción siempre es un porcentaje entre 0 y 100")
    void siempreEsUnPorcentaje() {
        double[] vacio = new double[modelo.numeroDeVariables()];
        double[] lleno = new double[modelo.numeroDeVariables()];
        lleno[ModeloOcupacion.HORA] = 19;
        lleno[ModeloOcupacion.SEMANA_ANTERIOR] = 500;
        lleno[ModeloOcupacion.RESERVAS_PCT] = 500;

        assertThat(modelo.predecir(vacio)).isBetween(0.0, 100.0);
        assertThat(modelo.predecir(lleno)).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("reconoce las zonas de Cartagena por su nombre y agrupa las demás como Otra")
    void zonas() {
        assertThat(modelo.indiceDeZona("Centro Histórico")).isEqualTo(0);
        assertThat(modelo.indiceDeZona("centro")).isEqualTo(0);
        assertThat(modelo.indiceDeZona("BOCAGRANDE")).isEqualTo(1);
        assertThat(modelo.indiceDeZona("Getsemaní")).isEqualTo(2);
        assertThat(modelo.indiceDeZona("Manga")).isEqualTo(3);
        assertThat(modelo.indiceDeZona("Pie de la Popa")).isEqualTo(4);
        assertThat(modelo.indiceDeZona(null)).isEqualTo(4);
    }

    @Test
    @DisplayName("el nivel es bajo por debajo del 50 %, medio hasta el 85 % y alto por encima")
    void niveles() {
        assertThat(Prediccion.Nivel.de(49.9)).isEqualTo(Prediccion.Nivel.BAJA);
        assertThat(Prediccion.Nivel.de(50)).isEqualTo(Prediccion.Nivel.MEDIA);
        assertThat(Prediccion.Nivel.de(85)).isEqualTo(Prediccion.Nivel.MEDIA);
        assertThat(Prediccion.Nivel.de(85.1)).isEqualTo(Prediccion.Nivel.ALTA);
    }

    @Test
    @DisplayName("el calendario conoce los festivos de Colombia, incluidos los que se trasladan al lunes")
    void festivos() {
        assertThat(CalendarioColombia.domingoDePascua(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(CalendarioColombia.festivos(2026)).hasSize(18).contains(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12),    // Reyes, trasladado
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 3),     // Jueves y Viernes Santo
                LocalDate.of(2026, 5, 18), LocalDate.of(2026, 6, 8),    // Ascensión y Corpus
                LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 25));
        assertThat(CalendarioColombia.esFestivo(LocalDate.of(2026, 1, 6))).isFalse();
        assertThat(CalendarioColombia.esTemporadaAlta(LocalDate.of(2026, 12, 24))).isTrue();
        assertThat(CalendarioColombia.esTemporadaAlta(LocalDate.of(2026, 9, 9))).isFalse();
    }
}
