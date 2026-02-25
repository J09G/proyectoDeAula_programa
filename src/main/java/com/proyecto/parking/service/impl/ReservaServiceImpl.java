package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.EmailService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.ReservaService;
import com.proyecto.parking.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservaServiceImpl implements ReservaService {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ParqueaderoRepository parqueaderoRepository;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private UsuarioService usuarioService;

    

    @Override
    public Reserva crearReserva(int idCliente, int idParqueadero) {
        Usuario cliente = usuarioService.obtenerUsuarioPorId(idCliente);
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

        List<Reserva> existentes = reservaRepository.findByClienteAndParqueadero(cliente, parqueadero);
        boolean tieneActiva = existentes.stream()
                .anyMatch(r -> r.getEstado() == EstadoReserva.PENDIENTE);
        if (tieneActiva) {
            throw new RuntimeException("Ya tienes una reserva activa en este parqueadero.");
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
    public List<Reserva> listarReservasCliente(int idCliente) {
        Usuario cliente = usuarioRepository.findById(idCliente)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado."));
        return reservaRepository.findByCliente(cliente);
    }

    @Override
    public List<Reserva> listarReservasParqueadero(int idParqueadero) {
        Parqueadero parqueadero = parqueaderoRepository.findById(idParqueadero)
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado."));
        return reservaRepository.findByParqueadero(parqueadero);
    }

    @Override
    public Reserva cambiarEstadoReserva(int idReserva, String nuevoEstado) {
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
    public void eliminarReserva(int idReserva) {
        if (!reservaRepository.existsById(idReserva)) {
            throw new RuntimeException("La reserva no existe.");
        }
        reservaRepository.deleteById(idReserva);
    }

    @Override
    public List<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, int idParqueadero) {
        Parqueadero parqueadero = parqueaderoRepository.findById(idParqueadero)
                .orElseThrow(() -> new RuntimeException("Parqueadero no encontrado."));

        Usuario cliente = usuarioRepository.findByCedula(cedula);
        if (cliente == null) {
            return List.of();
        }

        return reservaRepository.findByClienteAndParqueadero(cliente, parqueadero);
    }

    @Autowired
    private EmailService emailService;

    private void enviarCorreoCambioEstado(Reserva reserva) {
        String correo = reserva.getCliente().getCorreo();
        String nombre = reserva.getCliente().getNombre();
        String estado = reserva.getEstado().name();

        String asunto = "Actualización de tu reserva en ParkingApp";
        String mensaje = "Hola " + nombre + ",\n\n" +
                "Tu reserva con el ID #" + reserva.getIdReserva() + " ha sido " + estado.toLowerCase() + ".\n\n" +
                "Gracias por usar ParkingApp.";

        try {
            emailService.enviarCorreo(correo, asunto, mensaje);
            System.out.println("Correo enviado a " + correo + " correctamente.");
        } catch (Exception e) {
            System.err.println("Error al enviar correo al cliente: " + e.getMessage());
        }
    }

}
