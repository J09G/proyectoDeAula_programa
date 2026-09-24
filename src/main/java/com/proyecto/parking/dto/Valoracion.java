package com.proyecto.parking.dto;

/**
 * Valoración agregada de un parqueadero: promedio de estrellas y cuántas
 * personas lo han puntuado.
 *
 * @param promedio media de 1 a 5, o {@code null} si todavía nadie ha puntuado
 * @param total    número de comentarios con puntuación
 */
public record Valoracion(Double promedio, long total) {

    /** Parqueadero sin ninguna puntuación todavía. */
    public static Valoracion sinValoraciones() {
        return new Valoracion(null, 0);
    }

    public boolean tieneValoraciones() {
        return promedio != null && total > 0;
    }

    /** Promedio con un decimal, listo para pintar ("4,8"). */
    public String promedioFormateado() {
        return tieneValoraciones() ? String.format("%.1f", promedio) : "—";
    }
}
