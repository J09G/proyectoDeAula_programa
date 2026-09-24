package com.proyecto.parking.service;

import com.proyecto.parking.dto.ParqueaderoDestacado;
import com.proyecto.parking.dto.Valoracion;
import com.proyecto.parking.dto.ZonaDestacada;

import java.util.List;

/**
 * Datos que alimentan la portada pública y las vistas de exploración del
 * cliente: zonas con su número de parqueaderos y parqueaderos con su valoración.
 */
public interface PortadaService {

    /** Zonas visibles, con cuántos parqueaderos tiene cada una. */
    List<ZonaDestacada> zonasConConteo();

    /**
     * Parqueaderos habilitados para mostrar en la portada, mejor valorados
     * primero y con espacios libres por delante de los llenos.
     *
     * @param limite cuántos devolver como máximo
     */
    List<ParqueaderoDestacado> destacados(int limite);

    /** Los parqueaderos de una zona, con su valoración. */
    List<ParqueaderoDestacado> porZona(String idZona);

    /** Valoración de un único parqueadero. */
    Valoracion valoracionDe(String idParqueadero);
}
