package com.proyecto.parking.service;

import com.proyecto.parking.model.Zona;
import java.util.List;

public interface ZonaService {
    List<Zona> obtenerZonas();

    List<Zona> obtenerZonasHabilitadas();

    Zona obtenerZonaPorId(int idZona);

    Zona crearZona(String nombreZona);

    void cambiarEstadoZona(int idZona, boolean habilitado);
}
