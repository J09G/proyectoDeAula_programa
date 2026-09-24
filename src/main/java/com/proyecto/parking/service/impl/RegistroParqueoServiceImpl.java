package com.proyecto.parking.service.impl;

import com.proyecto.parking.dto.EntradaForm;
import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.RegistroParqueoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class RegistroParqueoServiceImpl implements RegistroParqueoService {

    private static final Logger log = LoggerFactory.getLogger(RegistroParqueoServiceImpl.class);

    /** Se cobra como mínimo una hora, aunque la estancia sea de pocos minutos. */
    static final BigDecimal HORAS_MINIMAS_FACTURABLES = BigDecimal.ONE;

    private final RegistroParqueoRepository registroRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ParqueaderoService parqueaderoService;

    public RegistroParqueoServiceImpl(RegistroParqueoRepository registroRepository,
                                      ReservaRepository reservaRepository,
                                      UsuarioRepository usuarioRepository,
                                      ParqueaderoService parqueaderoService) {
        this.registroRepository = registroRepository;
        this.reservaRepository = reservaRepository;
        this.usuarioRepository = usuarioRepository;
        this.parqueaderoService = parqueaderoService;
    }

    // ── Entrada ──────────────────────────────────────────────────────────────

    @Override
    public RegistroParqueo registrarEntrada(String idParqueadero, String idAdministrador, EntradaForm form) {
        Parqueadero parqueadero =
                parqueaderoService.obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);

        String placa = form.getPlaca();
        Integer espacio = form.getEspacioReservado();

        validarCubiculoExiste(parqueadero, espacio);
        validarPlacaSinRegistroActivo(placa);
        validarCubiculoLibre(idParqueadero, espacio);

        Usuario usuario = resolverUsuario(form.getCedula(), placa);
        Optional<Reserva> reserva = buscarReservaAceptada(usuario, idParqueadero);

        // Con reserva el cupo ya se descontó al aceptarla; sin reserva hay que
        // tomarlo ahora, y de forma atómica para no pasarse de la capacidad.
        boolean cupoTomadoAhora = false;
        if (reserva.isEmpty()) {
            validarCubiculoSinReservaDeTerceros(idParqueadero, espacio);
            parqueaderoService.ocuparEspacio(idParqueadero);
            cupoTomadoAhora = true;
        } else {
            validarCubiculoCoincideConReserva(reserva.get(), espacio);
        }

        try {
            RegistroParqueo registro = new RegistroParqueo();
            registro.setPlaca(placa);
            registro.setParqueadero(parqueadero);
            registro.setUsuario(usuario);
            registro.setEspacioReservado(espacio);
            registro.setHoraEntrada(LocalDateTime.now());
            registro.setEstado(EstadoRegistro.ACTIVO);
            // La tarifa se congela aquí: si el administrador la cambia mientras
            // el coche está dentro, se cobra la que había al entrar.
            registro.setTarifaHoraAplicada(parqueadero.getTarifaHora());

            reserva.ifPresent(r -> {
                r.setEstado(EstadoReserva.UTILIZADA);
                reservaRepository.save(r);
                registro.setReserva(r);
            });

            RegistroParqueo guardado = registroRepository.save(registro);
            log.info("Entrada registrada: placa {} en el cubículo {} del parqueadero {}.",
                    placa, espacio, idParqueadero);
            return guardado;

        } catch (RuntimeException e) {
            if (cupoTomadoAhora) {
                parqueaderoService.liberarEspacio(idParqueadero);
            }
            throw e;
        }
    }

    // ── Salida ───────────────────────────────────────────────────────────────

    @Override
    public RegistroParqueo registrarSalida(String idRegistro, String idAdministrador) {
        RegistroParqueo registro = obtenerPorId(idRegistro, idAdministrador);

        if (registro.getEstado() == EstadoRegistro.FINALIZADO) {
            throw new ReglaNegocioException("Este registro ya fue finalizado.");
        }

        LocalDateTime salida = LocalDateTime.now();
        long minutos = Math.max(0, ChronoUnit.MINUTES.between(registro.getHoraEntrada(), salida));

        registro.setHoraSalida(salida);
        registro.setTiempoMinutos(minutos);
        registro.setValorPagado(calcularValor(minutos, tarifaDe(registro)));
        registro.setEstado(EstadoRegistro.FINALIZADO);

        RegistroParqueo guardado = registroRepository.save(registro);
        parqueaderoService.liberarEspacio(registro.getParqueadero().getId());

        log.info("Salida registrada: placa {}, {} minutos, total {}.",
                registro.getPlaca(), minutos, guardado.getValorPagado());
        return guardado;
    }

    /**
     * Importe a cobrar: horas empezadas a la tarifa vigente, con un mínimo de una
     * hora. Se calcula con {@link BigDecimal} para evitar los redondeos raros del
     * {@code double} en importes de dinero.
     */
    static double calcularValor(long minutos, double tarifaHora) {
        BigDecimal horas = BigDecimal.valueOf(minutos)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                .max(HORAS_MINIMAS_FACTURABLES);

        return horas.multiply(BigDecimal.valueOf(tarifaHora))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Override
    public List<RegistroParqueo> listarActivosPorParqueadero(String idParqueadero, String idAdministrador) {
        parqueaderoService.obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);
        return registroRepository.findByParqueadero_IdAndEstado(idParqueadero, EstadoRegistro.ACTIVO);
    }

    @Override
    public Page<RegistroParqueo> listarHistorial(String idParqueadero, String idAdministrador,
                                                 Pageable pageable) {
        parqueaderoService.obtenerParqueaderoDeAdministrador(idParqueadero, idAdministrador);
        return registroRepository.findByParqueadero_Id(
                idParqueadero,
                PageableUtils.conOrdenPorDefecto(pageable, Sort.by(Sort.Direction.DESC, "_id")));
    }

    @Override
    public RegistroParqueo obtenerPorId(String idRegistro, String idAdministrador) {
        RegistroParqueo registro = registroRepository.findById(idRegistro)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Registro", idRegistro));

        // La propiedad se comprueba contra el parqueadero del registro, no contra
        // el id que venga en la URL.
        parqueaderoService.obtenerParqueaderoDeAdministrador(
                registro.getParqueadero().getId(), idAdministrador);

        return registro;
    }

    // ── Validaciones ─────────────────────────────────────────────────────────

    private void validarCubiculoExiste(Parqueadero parqueadero, Integer espacio) {
        if (espacio == null || espacio < 1 || espacio > parqueadero.getEspaciosTotales()) {
            throw new ReglaNegocioException(
                    "El cubículo " + espacio + " no existe en este parqueadero.");
        }
    }

    private void validarPlacaSinRegistroActivo(String placa) {
        registroRepository.findByPlacaAndEstado(placa, EstadoRegistro.ACTIVO)
                .ifPresent(r -> {
                    throw new ReglaNegocioException(
                            "La placa " + placa + " ya tiene una entrada activa sin salida registrada.");
                });
    }

    /** Antes no se comprobaba: se podía meter un segundo coche en un cubículo ocupado. */
    private void validarCubiculoLibre(String idParqueadero, Integer espacio) {
        registroRepository
                .findByParqueadero_IdAndEspacioReservadoAndEstado(idParqueadero, espacio, EstadoRegistro.ACTIVO)
                .ifPresent(r -> {
                    throw new ReglaNegocioException(
                            "El cubículo " + espacio + " ya está ocupado por la placa " + r.getPlaca() + ".");
                });
    }

    private void validarCubiculoSinReservaDeTerceros(String idParqueadero, Integer espacio) {
        if (!reservaRepository
                .findByParqueadero_IdAndEspacioReservadoAndEstado(idParqueadero, espacio, EstadoReserva.ACEPTADA)
                .isEmpty()) {
            throw new ReglaNegocioException(
                    "El cubículo " + espacio + " está reservado para otro cliente.");
        }
    }

    private void validarCubiculoCoincideConReserva(Reserva reserva, Integer espacio) {
        if (reserva.getEspacioReservado() != null && !reserva.getEspacioReservado().equals(espacio)) {
            throw new ReglaNegocioException(
                    "Este cliente tiene reservado el cubículo " + reserva.getEspacioReservado()
                    + ", no el " + espacio + ".");
        }
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /**
     * Identifica al cliente por cédula si el operario la introdujo; si no, lo
     * intenta por la placa. Un vehículo sin cliente registrado es válido: se
     * factura como cliente de paso.
     */
    private Usuario resolverUsuario(String cedula, String placa) {
        if (cedula != null && !cedula.isBlank()) {
            return usuarioRepository.findByCedula(cedula.trim())
                    .orElseThrow(() -> new ReglaNegocioException(
                            "No hay ningún usuario con la cédula " + cedula + "."));
        }
        return usuarioRepository.findByPlaca(placa).orElse(null);
    }

    private Optional<Reserva> buscarReservaAceptada(Usuario usuario, String idParqueadero) {
        if (usuario == null) {
            return Optional.empty();
        }
        return reservaRepository
                .findByCliente_IdAndParqueadero_IdAndEstado(usuario.getId(), idParqueadero, EstadoReserva.ACEPTADA)
                .stream()
                .findFirst();
    }

    private double tarifaDe(RegistroParqueo registro) {
        // Los registros creados antes de existir este campo caen en la tarifa actual.
        Double congelada = registro.getTarifaHoraAplicada();
        if (congelada != null) {
            return congelada;
        }
        Double actual = registro.getParqueadero().getTarifaHora();
        return actual != null ? actual : 0d;
    }
}
