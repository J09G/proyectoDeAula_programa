package com.proyecto.parking.service;

import com.proyecto.parking.model.Reserva;
import java.util.List;

public interface ReservaService {

    Reserva crearReserva(String idCliente, String idParqueadero);

    List<Reserva> listarReservasCliente(String idCliente);

    List<Reserva> listarReservasParqueadero(String idParqueadero);

    Reserva cambiarEstadoReserva(String idReserva, String nuevoEstado);

    void eliminarReserva(String idReserva);

    List<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, String idParqueadero);
}
