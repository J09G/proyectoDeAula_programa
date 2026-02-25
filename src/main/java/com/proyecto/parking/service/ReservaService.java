package com.proyecto.parking.service;

import com.proyecto.parking.model.Reserva;
import java.util.List;

public interface ReservaService {

    Reserva crearReserva(int idCliente, int idParqueadero);

    List<Reserva> listarReservasCliente(int idCliente);

    List<Reserva> listarReservasParqueadero(int idParqueadero);

    Reserva cambiarEstadoReserva(int idReserva, String nuevoEstado);

    void eliminarReserva(int idReserva);

    List<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, int idParqueadero);

}
