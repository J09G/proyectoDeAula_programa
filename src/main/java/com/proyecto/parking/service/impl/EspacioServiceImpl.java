package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.service.EspacioService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class EspacioServiceImpl implements EspacioService {

    private final ReservaRepository reservaRepository;
    private final RegistroParqueoRepository registroParqueoRepository;

    public EspacioServiceImpl(ReservaRepository reservaRepository,
                              RegistroParqueoRepository registroParqueoRepository) {
        this.reservaRepository = reservaRepository;
        this.registroParqueoRepository = registroParqueoRepository;
    }

    @Override
    public Set<Integer> calcularEspaciosOcupados(String idParqueadero) {
        Set<Integer> ocupados = new HashSet<>();

        reservaRepository
                .findByParqueadero_IdAndEstado(idParqueadero, Reserva.EstadoReserva.ACEPTADA)
                .stream()
                .map(Reserva::getEspacioReservado)
                .filter(java.util.Objects::nonNull)
                .forEach(ocupados::add);

        registroParqueoRepository
                .findByParqueadero_IdAndEstado(idParqueadero, RegistroParqueo.EstadoRegistro.ACTIVO)
                .stream()
                .map(RegistroParqueo::getEspacioReservado)
                .filter(java.util.Objects::nonNull)
                .forEach(ocupados::add);

        return ocupados;
    }

    @Override
    public Set<Integer> calcularEspaciosPendientes(String idParqueadero) {
        Set<Integer> pendientes = new HashSet<>();

        reservaRepository
                .findByParqueadero_IdAndEstado(idParqueadero, Reserva.EstadoReserva.PENDIENTE)
                .stream()
                .map(Reserva::getEspacioReservado)
                .filter(java.util.Objects::nonNull)
                .forEach(pendientes::add);

        return pendientes;
    }

    @Override
    public boolean estaComprometido(String idParqueadero, Integer espacio) {
        if (espacio == null) {
            return false;
        }
        return calcularEspaciosOcupados(idParqueadero).contains(espacio)
                || calcularEspaciosPendientes(idParqueadero).contains(espacio);
    }
}
