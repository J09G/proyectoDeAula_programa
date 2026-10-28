package com.proyecto.parking.service;

import com.proyecto.parking.prediccion.Prediccion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Ocupación estimada de un parqueadero, según el modelo predictivo. */
public interface PrediccionService {

    /** Una predicción por cada franja horaria que cubre el modelo, para el día indicado. */
    List<Prediccion> predecirDia(String idParqueadero, LocalDate dia);

    /**
     * Predicción para la franja en la que cae un momento. Fuera del horario que
     * cubre el modelo se devuelve la franja más cercana.
     */
    Prediccion predecir(String idParqueadero, LocalDateTime momento);
}
