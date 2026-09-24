package com.proyecto.parking.service;

import java.util.Set;

/** Consulta del estado de ocupación de los cubículos de un parqueadero. */
public interface EspacioService {

    /** Cubículos con un vehículo dentro o con una reserva ya aceptada. */
    Set<Integer> calcularEspaciosOcupados(String idParqueadero);

    /** Cubículos con una reserva pendiente de aprobación. */
    Set<Integer> calcularEspaciosPendientes(String idParqueadero);

    /**
     * @return {@code true} si el cubículo no se puede asignar ahora mismo, ya sea
     *         porque está ocupado o porque hay una solicitud pendiente sobre él.
     */
    boolean estaComprometido(String idParqueadero, Integer espacio);
}
