package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.dto.ReservaForm;
import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.EmailService;
import com.proyecto.parking.service.EspacioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.ReservaService;
import com.proyecto.parking.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservaServiceImpl implements ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaServiceImpl.class);

    /** Estados que impiden al cliente pedir otra reserva en el mismo parqueadero. */
    private static final List<EstadoReserva> ESTADOS_ABIERTOS =
            List.of(EstadoReserva.PENDIENTE, EstadoReserva.ACEPTADA);

    private final ReservaRepository reservaRepository;
    private final RegistroParqueoRepository registroParqueoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final ParqueaderoService parqueaderoService;
    private final EspacioService espacioService;
    private final EmailService emailService;
    private final ParkingProperties properties;

    public ReservaServiceImpl(ReservaRepository reservaRepository,
                              RegistroParqueoRepository registroParqueoRepository,
                              UsuarioRepository usuarioRepository,
                              UsuarioService usuarioService,
                              ParqueaderoService parqueaderoService,
                              EspacioService espacioService,
                              EmailService emailService,
                              ParkingProperties properties) {
        this.reservaRepository = reservaRepository;
        this.registroParqueoRepository = registroParqueoRepository;
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.parqueaderoService = parqueaderoService;
        this.espacioService = espacioService;
        this.emailService = emailService;
        this.properties = properties;
    }

    // ── Cliente ──────────────────────────────────────────────────────────────

    @Override
    public Reserva crearReserva(String idCliente, ReservaForm form) {
        Usuario cliente = usuarioService.obtenerUsuarioPorId(idCliente);
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(form.getIdParqueadero());
        Integer espacio = form.getEspacioReservado();

        if (!Boolean.TRUE.equals(parqueadero.getHabilitado())) {
            throw new ReglaNegocioException("Este parqueadero no está aceptando reservas.");
        }
        if (parqueadero.getAdministrador() == null) {
            throw new ReglaNegocioException(
                    "Este parqueadero no tiene administrador asignado y no acepta reservas.");
        }
        if (espacio < 1 || espacio > parqueadero.getEspaciosTotales()) {
            throw new ReglaNegocioException(
                    "El cubículo " + espacio + " no existe en este parqueadero.");
        }
        if (!registroParqueoRepository.findByUsuario_IdAndEstado(idCliente, EstadoRegistro.ACTIVO).isEmpty()) {
            throw new ReglaNegocioException(
                    "No puedes reservar mientras tienes un vehículo dentro de un parqueadero.");
        }
        if (tieneReservaAbierta(idCliente, parqueadero.getId())) {
            throw new ReglaNegocioException("Ya tienes una reserva activa en este parqueadero.");
        }
        // Se valida también en el servidor: la cuadrícula del formulario sólo
        // deshabilita los cubículos ocupados de cara al usuario.
        if (espacioService.estaComprometido(parqueadero.getId(), espacio)) {
            throw new ReglaNegocioException("El cubículo " + espacio + " ya no está disponible.");
        }
        if (parqueadero.getEspaciosDisponibles() <= 0) {
            throw new ReglaNegocioException("No hay espacios disponibles en este parqueadero.");
        }

        Reserva nueva = new Reserva();
        nueva.setCliente(cliente);
        nueva.setParqueadero(parqueadero);
        nueva.setEstado(EstadoReserva.PENDIENTE);
        nueva.setFechaReserva(form.getFechaReserva());
        nueva.setEspacioReservado(espacio);

        Reserva guardada = reservaRepository.save(nueva);
        log.info("Reserva {} creada por el cliente {} en el parqueadero {}.",
                guardada.getId(), idCliente, parqueadero.getId());
        return guardada;
    }

    @Override
    public List<Reserva> listarReservasCliente(String idCliente) {
        return reservaRepository.findByCliente_Id(idCliente, Sort.by(Sort.Direction.DESC, "_id"));
    }

    // ── Administrador ────────────────────────────────────────────────────────

    @Override
    public List<Reserva> listarReservasParqueadero(String idParqueadero, String idAdministrador) {
        parqueaderoService.obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);
        return reservaRepository.findByParqueadero_Id(idParqueadero, Sort.by(Sort.Direction.DESC, "_id"));
    }

    @Override
    public List<Reserva> buscarReservasPorCedulaYParqueadero(String cedula, String idParqueadero,
                                                             String idAdministrador) {
        parqueaderoService.obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);

        return usuarioRepository.findByCedula(cedula.trim())
                .map(cliente -> reservaRepository.findByCliente_IdAndParqueadero_Id(cliente.getId(), idParqueadero))
                .orElseGet(List::of);
    }

    @Override
    public Reserva aceptarReserva(String idReserva, String idAdministrador) {
        Reserva reserva = cargarReservaDelAdministrador(idReserva, idAdministrador);
        exigirPendiente(reserva);

        String idParqueadero = reserva.getParqueadero().getId();
        Integer espacio = reserva.getEspacioReservado();

        if (espacio != null && !reservaRepository
                .findByParqueadero_IdAndEspacioReservadoAndEstado(idParqueadero, espacio, EstadoReserva.ACEPTADA)
                .isEmpty()) {
            throw new ReglaNegocioException(
                    "El cubículo " + espacio + " ya fue asignado a otra reserva.");
        }

        // Se toma el cupo primero: si no quedaba, la reserva no cambia de estado.
        parqueaderoService.ocuparEspacio(idParqueadero);

        try {
            reserva.setEstado(EstadoReserva.ACEPTADA);
            reservaRepository.save(reserva);
        } catch (RuntimeException e) {
            // MongoDB no nos da una transacción entre los dos documentos, así que
            // si el guardado falla devolvemos el cupo a mano para no perderlo.
            parqueaderoService.liberarEspacio(idParqueadero);
            throw e;
        }

        log.info("Reserva {} aceptada por el administrador {}.", idReserva, idAdministrador);
        notificarCambioEstado(reserva);
        return reserva;
    }

    @Override
    public Reserva rechazarReserva(String idReserva, String idAdministrador) {
        Reserva reserva = cargarReservaDelAdministrador(idReserva, idAdministrador);
        exigirPendiente(reserva);

        // Una reserva pendiente todavía no había tomado cupo: no hay nada que devolver.
        reserva.setEstado(EstadoReserva.RECHAZADA);
        reservaRepository.save(reserva);

        log.info("Reserva {} rechazada por el administrador {}.", idReserva, idAdministrador);
        notificarCambioEstado(reserva);
        return reserva;
    }

    @Override
    public void eliminarReserva(String idReserva, String idAdministrador) {
        Reserva reserva = cargarReservaDelAdministrador(idReserva, idAdministrador);

        // Si estaba aceptada tenía un cupo bloqueado. Antes se borraba sin
        // devolverlo y el parqueadero perdía ese espacio para siempre.
        if (reserva.getEstado().bloqueaEspacio()) {
            parqueaderoService.liberarEspacio(reserva.getParqueadero().getId());
        }

        reservaRepository.deleteById(idReserva);
        log.info("Reserva {} eliminada por el administrador {}.", idReserva, idAdministrador);
    }

    // ── Mantenimiento ────────────────────────────────────────────────────────

    @Override
    public int expirarReservasVencidas() {
        LocalDateTime limite = LocalDateTime.now()
                .minusMinutes(properties.getReservas().getMinutosTolerancia());

        List<Reserva> vencidas =
                reservaRepository.findByEstadoAndFechaReservaBefore(EstadoReserva.ACEPTADA, limite);

        int expiradas = 0;
        for (Reserva reserva : vencidas) {
            try {
                reserva.setEstado(EstadoReserva.EXPIRADA);
                reservaRepository.save(reserva);
                parqueaderoService.liberarEspacio(reserva.getParqueadero().getId());
                notificarCambioEstado(reserva);
                expiradas++;
            } catch (RuntimeException e) {
                // Una reserva problemática no debe impedir liberar las demás.
                log.error("No se pudo expirar la reserva {}: {}", reserva.getId(), e.getMessage());
            }
        }

        if (expiradas > 0) {
            log.info("{} reservas expiradas; sus cubículos vuelven a estar disponibles.", expiradas);
        }
        return expiradas;
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private boolean tieneReservaAbierta(String idCliente, String idParqueadero) {
        return reservaRepository
                .findByCliente_IdAndEstadoIn(idCliente, ESTADOS_ABIERTOS)
                .stream()
                .anyMatch(r -> r.getParqueadero() != null
                        && idParqueadero.equals(r.getParqueadero().getId()));
    }

    private Reserva cargarReservaDelAdministrador(String idReserva, String idAdministrador) {
        Reserva reserva = reservaRepository.findById(idReserva)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Reserva", idReserva));

        // La comprobación de propiedad va sobre el parqueadero de la reserva, no
        // sobre el id que venga en la URL: así no se puede aceptar la reserva de
        // otro administrador colando su id de parqueadero en la ruta.
        parqueaderoService.obtenerParqueaderoDeAdministrador(
                reserva.getParqueadero().getId(), idAdministrador);

        return reserva;
    }

    private void exigirPendiente(Reserva reserva) {
        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new ReglaNegocioException(
                    "Sólo se pueden gestionar reservas pendientes. Esta está en estado "
                    + reserva.getEstado() + ".");
        }
    }

    private void notificarCambioEstado(Reserva reserva) {
        Usuario cliente = reserva.getCliente();
        if (cliente == null || cliente.getCorreo() == null) {
            return;
        }

        String asunto = "Actualización de tu reserva en ParkingApp";
        String mensaje = """
                Hola %s,

                Tu reserva #%s en %s ha pasado al estado: %s.

                Cubículo: %s
                Fecha de llegada: %s

                Gracias por usar ParkingApp.
                """.formatted(
                        cliente.getNombre(),
                        reserva.getId(),
                        reserva.getParqueadero().getNombre(),
                        reserva.getEstado().name().toLowerCase(),
                        reserva.getEspacioReservado(),
                        reserva.getFechaReserva());

        emailService.enviarCorreo(cliente.getCorreo(), asunto, mensaje);
    }
}
