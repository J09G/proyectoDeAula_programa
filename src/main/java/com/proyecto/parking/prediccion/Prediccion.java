package com.proyecto.parking.prediccion;

import java.time.LocalDateTime;

/**
 * Ocupación estimada de un parqueadero en una franja de una hora.
 *
 * @param franja       inicio de la franja
 * @param hora         hora del día (0-23)
 * @param porcentaje   ocupación estimada, de 0 a 100
 * @param nivel        BAJA (menos del 50 %), MEDIA (50-85 %) o ALTA (más del 85 %)
 * @param conHistorial {@code false} si el parqueadero no tenía registros de la
 *                     semana anterior y se usó el comportamiento típico de su zona
 */
public record Prediccion(LocalDateTime franja, int hora, int porcentaje, Nivel nivel, boolean conHistorial) {

    public enum Nivel {
        BAJA, MEDIA, ALTA;

        public static Nivel de(double porcentaje) {
            return porcentaje > 85 ? ALTA : porcentaje >= 50 ? MEDIA : BAJA;
        }
    }
}
