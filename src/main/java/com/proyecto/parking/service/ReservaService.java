package com.proyecto.parking.service;

import com.proyecto.parking.dto.ReservaForm;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ReservaService {

    Reserva crearReserva(String idCliente, ReservaForm form);

    List<Reserva> listarReservasCliente(String idCliente);

    /**
     * El cliente cancela su propia reserva pendiente o aceptada. Si estaba
     * aceptada, el cubículo vuelve al inventario del parqueadero.
     *
     * @throws com.proyecto.parking.exception.RecursoNoEncontradoException
     *         si la reserva no existe o es de otro cliente (mismo error a propósito)
     * @throws com.proyecto.parking.exception.ReglaNegocioException
     *         si ya no está en un estado cancelable
     */
    Reserva cancelarReserva(String idReserva, String idCliente);

    /** Reservas del parqueadero, por páginas, comprobando antes que sea del administrador. */
    Page<Reserva> listarReservasParqueadero(String idParqueadero, String idAdministrador, Pageable pageable);

    Page<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, String idParqueadero, String idAdministrador,
                                                      Pageable pageable);

    /** Cuántas reservas del parqueadero están en ese estado, en todas las páginas. */
    long contarReservasPorEstado(String idParqueadero, String idAdministrador, EstadoReserva estado);

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
