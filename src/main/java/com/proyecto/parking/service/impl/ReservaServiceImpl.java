package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.EmailService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.ReservaService;
import com.proyecto.parking.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.springframework.data.domain.Sort;
import java.util.List;

@Service
public class ReservaServiceImpl implements ReservaService {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private EmailService emailService;

    @Override
    public Reserva crearReserva(String idCliente, String idParqueadero) {
        Usuario cliente = usuarioService.obtenerUsuarioPorId(idCliente);
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

        boolean tieneActiva = !reservaRepository
                .findByCliente_IdAndParqueadero_IdAndEstado(cliente.getId(), parqueadero.getId(), EstadoReserva.PENDIENTE)
                .isEmpty();
        if (tieneActiva) {
            throw new RuntimeException("Ya tienes una reserva activa en este parqueadero.");
        }

        if (parqueadero.getAdministrador() == null) {
            throw new RuntimeException("Este parqueadero no tiene administrador asignado y no acepta reservas.");
        }

        if (parqueadero.getEspaciosDisponibles() <= 0) {
            throw new RuntimeException("No hay espacios disponibles en este parqueadero.");
        }

        Reserva nueva = new Reserva();
        nueva.setCliente(cliente);
        nueva.setParqueadero(parqueadero);
        nueva.setEstado(EstadoReserva.PENDIENTE);

        return reservaRepository.save(nueva);
    }

    @Override
    public List<Reserva> listarReservasCliente(String idCliente) {
        return reservaRepository.findByCliente_Id(idCliente, Sort.by(Sort.Direction.DESC, "_id"));
    }

    @Override
    public List<Reserva> listarReservasParqueadero(String idParqueadero) {
        return reservaRepository.findByParqueadero_Id(idParqueadero);
    }

    @Override
    public Reserva cambiarEstadoReserva(String idReserva, String nuevoEstado) {
        Reserva reserva = reservaRepository.findById(idReserva)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada."));

        EstadoReserva estadoNuevo;
        try {
            estadoNuevo = EstadoReserva.valueOf(nuevoEstado.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Estado no válido. Usa: PENDIENTE, ACEPTADA o RECHAZADA.");
        }

        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new RuntimeException("Solo se pueden modificar reservas en estado PENDIENTE.");
        }

        reserva.setEstado(estadoNuevo);

        if (estadoNuevo == EstadoReserva.ACEPTADA) {
            Parqueadero parqueadero = reserva.getParqueadero();
            if (parqueadero.getEspaciosDisponibles() <= 0) {
                throw new RuntimeException("No hay espacios disponibles para aceptar la reserva.");
            }
            parqueadero.setEspaciosDisponibles(parqueadero.getEspaciosDisponibles() - 1);
            parqueaderoService.guardarParqueadero(parqueadero);
        }

        reservaRepository.save(reserva);
        enviarCorreoCambioEstado(reserva);

        return reserva;
    }

    @Override
    public void eliminarReserva(String idReserva) {
        if (!reservaRepository.existsById(idReserva)) {
            throw new RuntimeException("La reserva no existe.");
        }
        reservaRepository.deleteById(idReserva);
    }

    @Override
    public List<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, String idParqueadero) {
        Usuario cliente = usuarioRepository.findByCedula(cedula);
        if (cliente == null) {
            return List.of();
        }
        return reservaRepository.findByCliente_IdAndParqueadero_Id(cliente.getId(), idParqueadero);
    }

    private void enviarCorreoCambioEstado(Reserva reserva) {
        String correo = reserva.getCliente().getCorreo();
        String nombre = reserva.getCliente().getNombre();
        String estado = reserva.getEstado().name();

        String asunto = "Actualización de tu reserva en ParkingApp";
        String mensaje = "Hola " + nombre + ",\n\n" +
                "Tu reserva con el ID #" + reserva.getId() + " ha sido " + estado.toLowerCase() + ".\n\n" +
                "Gracias por usar ParkingApp.";

        try {
            emailService.enviarCorreo(correo, asunto, mensaje);
        } catch (Exception e) {
            System.err.println("Error al enviar correo al cliente: " + e.getMessage());
        }
    }
}
