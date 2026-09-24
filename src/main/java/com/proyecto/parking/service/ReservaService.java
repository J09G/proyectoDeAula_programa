package com.proyecto.parking.service;

import com.proyecto.parking.dto.ReservaForm;
import com.proyecto.parking.model.Reserva;

import java.util.List;

public interface ReservaService {

    Reserva crearReserva(String idCliente, ReservaForm form);

    List<Reserva> listarReservasCliente(String idCliente);

    /** Reservas del parqueadero, comprobando antes que sea del administrador. */
    List<Reserva> listarReservasParqueadero(String idParqueadero, String idAdministrador);

    List<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, String idParqueadero, String idAdministrador);

    Reserva aceptarReserva(String idReserva, String idAdministrador);

    Reserva rechazarReserva(String idReserva, String idAdministrador);

    void eliminarReserva(String idReserva, String idAdministrador);

    /**
     * Marca como EXPIRADA toda reserva aceptada cuya hora de llegada pasó hace
     * más de la tolerancia configurada, y devuelve su cubículo al inventario.
     *
     * @return número de reservas expiradas
     */
    int expirarReservasVencidas();
}
