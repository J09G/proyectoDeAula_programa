package com.proyecto.parking.prediccion;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * Festivos de Colombia (Ley 51 de 1983) y temporada turística alta de Cartagena.
 *
 * <p>Son las mismas reglas con las que se generó el conjunto de datos de
 * entrenamiento ({@code ml/calendario.py}): si cambian aquí, deben cambiar allí.</p>
 */
public final class CalendarioColombia {

    private static final int[][] FIJOS = {{1, 1}, {5, 1}, {7, 20}, {8, 7}, {12, 8}, {12, 25}};
    /** Se celebran el lunes siguiente si no caen en lunes. */
    private static final int[][] TRASLADABLES = {{1, 6}, {3, 19}, {6, 29}, {8, 15}, {10, 12}, {11, 1}, {11, 11}};

    private CalendarioColombia() {
    }

    /** Algoritmo de Butcher-Meeus para el calendario gregoriano. */
    public static LocalDate domingoDePascua(int anio) {
        int a = anio % 19;
        int b = anio / 100;
        int c = anio % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int n = h + l - 7 * m + 114;
        return LocalDate.of(anio, n / 31, n % 31 + 1);
    }

    public static Set<LocalDate> festivos(int anio) {
        Set<LocalDate> dias = new HashSet<>();
        for (int[] f : FIJOS) {
            dias.add(LocalDate.of(anio, f[0], f[1]));
        }
        for (int[] t : TRASLADABLES) {
            dias.add(LocalDate.of(anio, t[0], t[1]).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
        }
        LocalDate pascua = domingoDePascua(anio);
        dias.add(pascua.minusDays(3));   // Jueves Santo
        dias.add(pascua.minusDays(2));   // Viernes Santo
        dias.add(pascua.plusDays(43));   // Ascensión
        dias.add(pascua.plusDays(64));   // Corpus Christi
        dias.add(pascua.plusDays(71));   // Sagrado Corazón
        return dias;
    }

    public static boolean esFestivo(LocalDate dia) {
        return festivos(dia.getYear()).contains(dia);
    }

    public static boolean esFinDeSemana(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Temporadas de mayor llegada de visitantes a Cartagena. */
    public static boolean esTemporadaAlta(LocalDate dia) {
        LocalDate pascua = domingoDePascua(dia.getYear());
        if (!dia.isBefore(pascua.minusDays(7)) && !dia.isAfter(pascua)) {
            return true;   // Semana Santa
        }
        int mes = dia.getMonthValue();
        int d = dia.getDayOfMonth();
        return (mes == 12 && d >= 15) || (mes == 1 && d <= 15)     // fin de año
                || (mes == 6 && d >= 15) || (mes == 7 && d <= 15)  // mitad de año
                || (mes == 11 && d >= 8 && d <= 17);               // fiestas de la Independencia
    }
}
