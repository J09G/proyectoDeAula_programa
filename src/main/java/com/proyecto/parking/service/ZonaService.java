package com.proyecto.parking.service;

import com.proyecto.parking.model.Zona;
import java.util.List;

public interface ZonaService {
    List<Zona> obtenerZonas();
    
    Zona obtenerZonaPorId(int idZona);
}
