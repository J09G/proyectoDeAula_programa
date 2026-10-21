package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Pago;
import com.proyecto.parking.model.Pago.EstadoPago;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.PagoRepository;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.PagoService;
import com.proyecto.parking.service.PasarelaPago;
import com.proyecto.parking.service.PasarelaPago.Transaccion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PagoServiceImpl implements PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoServiceImpl.class);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** De más a menos relevante al elegir qué pago mostrar de una reserva. */
    private static final List<EstadoPago> PRIORIDAD = List.of(
            EstadoPago.APROBADO, EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO,
            EstadoPago.RETENIDO, EstadoPago.PENDIENTE, EstadoPago.RECHAZADO);

    private final PagoRepository pagoRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ParqueaderoRepository parqueaderoRepository;
    private final PasarelaPago pasarela;
    private final ParkingProperties properties;

    public PagoServiceImpl(PagoRepository pagoRepository,
                           ReservaRepository reservaRepository,
                           UsuarioRepository usuarioRepository,
                           ParqueaderoRepository parqueaderoRepository,
                           PasarelaPago pasarela,
                           ParkingProperties properties) {
        this.pagoRepository = pagoRepository;
        this.reservaRepository = reservaRepository;
        this.usuarioRepository = usuarioRepository;
        this.parqueaderoRepository = parqueaderoRepository;
        this.pasarela = pasarela;
        this.properties = properties;
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @Override
    public boolean pagosHabilitados() {
        return properties.getPagos().isHabilitados();
    }

    @Override
    public double calcularAnticipo(Parqueadero parqueadero) {
        double tarifa = parqueadero.getTarifaHora() != null ? parqueadero.getTarifaHora() : 0d;
        // La pasarela no acepta cobros por debajo de su mínimo.
        return Math.max(tarifa, properties.getPagos().getMontoMinimo());
    }

    @Override
    public long minutosAntelacionDevolucion() {
        return properties.getPagos().getMinutosAntelacionDevolucion();
    }

    @Override
    public LocalDateTime limiteParaPagar(Reserva reserva) {
        LocalDateTime creada = reserva.getCreadaEn() != null ? reserva.getCreadaEn() : LocalDateTime.now();
        return creada.plusMinutes(properties.getPagos().getMinutosParaPagar());
    }

    @Override
    public boolean cancelarDaDerechoADevolucion(Reserva reserva) {
        // Mientras el parqueadero no la acepte, no se ha comprometido con el
        // cliente ni ha guardado el cubículo para él: cancelar siempre devuelve.
        if (reserva.getEstado() != EstadoReserva.ACEPTADA) {
            return true;
        }
        if (reserva.getFechaReserva() == null) {
            return false;
        }
        LocalDateTime limite = reserva.getFechaReserva()
                .minusMinutes(properties.getPagos().getMinutosAntelacionDevolucion());
        return !LocalDateTime.now().isAfter(limite);
    }

    @Override
    public Map<String, Pago> pagosPorReserva(Collection<String> idsReserva) {
        if (idsReserva == null || idsReserva.isEmpty()) {
            return Map.of();
        }
        return pagoRepository.findByIdReservaInAndEstadoIn(idsReserva, PRIORIDAD).stream()
                .collect(Collectors.toMap(Pago::getIdReserva, p -> p,
                        (a, b) -> PRIORIDAD.indexOf(a.getEstado()) <= PRIORIDAD.indexOf(b.getEstado()) ? a : b));
    }

    @Override
    public Optional<Pago> pagoDeReserva(String idReserva) {
        return pagoRepository.findByIdReserva(idReserva).stream()
                .min(Comparator.comparingInt(p -> PRIORIDAD.indexOf(p.getEstado())));
    }

    // ── Pagar ────────────────────────────────────────────────────────────────

    @Override
    public String iniciarPago(String idReserva, String idCliente) {
        // Una reserva ajena responde igual que una inexistente, como en el resto de la app.
        Reserva reserva = reservaRepository.findById(idReserva)
                .filter(r -> r.getCliente() != null && idCliente.equals(r.getCliente().getId()))
                .orElseThrow(() -> RecursoNoEncontradoException.de("Reserva", idReserva));

        if (!pagosHabilitados()) {
            throw new ReglaNegocioException("Los pagos en línea no están disponibles en este momento.");
        }
        if (reserva.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            throw new ReglaNegocioException("Esta reserva no tiene un pago pendiente.");
        }
        if (LocalDateTime.now().isAfter(limiteParaPagar(reserva))) {
            throw new ReglaNegocioException(
                    "Se venció el plazo para pagar esta reserva. Crea una nueva para apartar el cubículo.");
        }

        Pago pago = new Pago();
        pago.setIdReserva(reserva.getId());
        pago.setIdCliente(idCliente);
        pago.setIdParqueadero(reserva.getParqueadero().getId());
        pago.setReferencia(nuevaReferencia(reserva.getId()));
        pago.setMontoCentavos(aCentavos(reserva.getAnticipo()));
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setCreadoEn(LocalDateTime.now());
        pago.setActualizadoEn(pago.getCreadoEn());
        pagoRepository.save(pago);

        log.info("Pago {} iniciado para la reserva {} ({} centavos).",
                pago.getReferencia(), idReserva, pago.getMontoCentavos());
        return pasarela.urlDePago(pago, properties.getUrlPublica() + "/pagos/wompi/retorno");
    }

    @Override
    public Resultado confirmarTransaccion(String idTransaccion) {
        return pasarela.consultarTransaccion(idTransaccion)
                .map(this::aplicar)
                .orElse(Resultado.NO_ENCONTRADO);
    }

    @Override
    public void procesarAviso(Map<String, Object> aviso) {
        if (!pasarela.avisoEsAutentico(aviso)) {
            log.warn("Aviso de pago descartado: no trae la firma de la pasarela.");
            return;
        }
        // La firma dice que el aviso es de la pasarela, pero el estado se lee
        // igualmente de su API: así un aviso viejo reenviado no cambia nada.
        if (aviso.get("data") instanceof Map<?, ?> datos
                && datos.get("transaction") instanceof Map<?, ?> transaccion
                && transaccion.get("id") != null) {
            confirmarTransaccion(String.valueOf(transaccion.get("id")));
        }
    }

    @Override
    public boolean recuperarPagoPerdido(Reserva reserva) {
        for (Pago pago : pagoRepository.findByIdReserva(reserva.getId())) {
            if (pago.getEstado() == EstadoPago.PENDIENTE
                    && pasarela.consultarPorReferencia(pago.getReferencia()).map(this::aplicar).orElse(null)
                            == Resultado.APROBADO) {
                return true;
            }
        }
        return false;
    }

    /** Aplica a nuestros datos lo que la pasarela dice de una transacción. Es idempotente. */
    private Resultado aplicar(Transaccion transaccion) {
        Optional<Pago> encontrado = pagoRepository.findByReferencia(transaccion.referencia());
        if (encontrado.isEmpty()) {
            log.warn("La transacción {} trae una referencia desconocida: {}.",
                    transaccion.id(), transaccion.referencia());
            return Resultado.NO_ENCONTRADO;
        }
        Pago pago = encontrado.get();

        if (pago.getEstado() == EstadoPago.APROBADO) {
            return Resultado.APROBADO;
        }
        if (pago.getEstado() != EstadoPago.PENDIENTE && pago.getEstado() != EstadoPago.RECHAZADO) {
            // Ya devuelto o retenido: no hay nada más que hacer con él.
            return Resultado.APROBADO_SIN_RESERVA;
        }
        if (transaccion.pendiente()) {
            return Resultado.EN_PROCESO;
        }
        if (!transaccion.aprobada()) {
            marcar(pago, EstadoPago.PENDIENTE, EstadoPago.RECHAZADO, transaccion);
            return Resultado.RECHAZADO;
        }
        // Aprobada, pero por otro importe o moneda: alguien pagó menos de lo debido.
        if (transaccion.montoCentavos() != pago.getMontoCentavos()
                || !pago.getMoneda().equals(transaccion.moneda())) {
            log.warn("Transacción {} rechazada: llegó {} {} y se esperaban {} {}.",
                    transaccion.id(), transaccion.montoCentavos(), transaccion.moneda(),
                    pago.getMontoCentavos(), pago.getMoneda());
            marcar(pago, pago.getEstado(), EstadoPago.RECHAZADO, transaccion);
            return Resultado.RECHAZADO;
        }
        // Sólo uno de los procesos que lleguen a la vez consigue aprobarlo.
        if (!marcar(pago, pago.getEstado(), EstadoPago.APROBADO, transaccion)) {
            return Resultado.APROBADO;
        }

        // El anticipo ya está cobrado: la reserva pasa a manos del parqueadero.
        if (reservaRepository.cambiarEstadoSi(
                pago.getIdReserva(), EstadoReserva.PENDIENTE_PAGO, EstadoReserva.PENDIENTE) == 0) {
            // El cliente la canceló o se venció el plazo mientras pagaba.
            log.warn("El pago {} se aprobó pero la reserva {} ya no esperaba pago: se devuelve.",
                    pago.getReferencia(), pago.getIdReserva());
            devolver(pago);
            return Resultado.APROBADO_SIN_RESERVA;
        }
        log.info("Pago {} aprobado: la reserva {} queda pendiente de confirmación.",
                pago.getReferencia(), pago.getIdReserva());
        return Resultado.APROBADO;
    }

    // ── Devolver o retener ───────────────────────────────────────────────────

    @Override
    public void devolverAnticipo(Reserva reserva) {
        pagoRepository.findFirstByIdReservaAndEstado(reserva.getId(), EstadoPago.APROBADO)
                .ifPresent(this::devolver);
    }

    @Override
    public void retenerAnticipo(Reserva reserva) {
        pagoRepository.findFirstByIdReservaAndEstado(reserva.getId(), EstadoPago.APROBADO)
                .ifPresent(pago -> {
                    if (pagoRepository.cambiarEstadoSi(pago.getId(), EstadoPago.APROBADO, EstadoPago.RETENIDO) > 0) {
                        log.info("Anticipo {} retenido: la reserva {} no se usó.",
                                pago.getReferencia(), pago.getIdReserva());
                    }
                });
    }

    private void devolver(Pago pago) {
        // Primero se reserva el cambio de estado: dos devoluciones a la vez no deben anular dos veces.
        if (pagoRepository.cambiarEstadoSi(pago.getId(), EstadoPago.APROBADO, EstadoPago.DEVOLUCION_PENDIENTE) == 0) {
            return;
        }
        if (pasarela.devolver(pago.getIdTransaccion())) {
            pagoRepository.cambiarEstadoSi(pago.getId(), EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO);
            log.info("Anticipo {} devuelto al cliente.", pago.getReferencia());
        } else {
            log.warn("El anticipo {} queda pendiente de devolución manual (transacción {}).",
                    pago.getReferencia(), pago.getIdTransaccion());
        }
    }

    // ── Devoluciones manuales ────────────────────────────────────────────────

    @Override
    public List<DevolucionPendiente> listarDevolucionesPendientes() {
        // La más antigua primero: es la del cliente que lleva más tiempo esperando.
        List<Pago> pagos = pagoRepository.findByEstado(
                EstadoPago.DEVOLUCION_PENDIENTE, Sort.by(Sort.Direction.ASC, "actualizadoEn"));
        if (pagos.isEmpty()) {
            return List.of();
        }
        // Dos consultas en total, no una por fila.
        Map<String, Usuario> clientes = usuarioRepository
                .findAllById(idsDistintos(pagos, Pago::getIdCliente)).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u));
        Map<String, Parqueadero> parqueaderos = parqueaderoRepository
                .findAllById(idsDistintos(pagos, Pago::getIdParqueadero)).stream()
                .collect(Collectors.toMap(Parqueadero::getId, p -> p));

        return pagos.stream()
                .map(p -> new DevolucionPendiente(p,
                        clientes.get(p.getIdCliente()), parqueaderos.get(p.getIdParqueadero())))
                .toList();
    }

    @Override
    public void marcarDevuelto(String idPago, String idSuperadmin) {
        // Atómico: si dos superadministradores pulsan a la vez, solo uno lo marca.
        if (pagoRepository.cambiarEstadoSi(idPago, EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO) == 0) {
            throw new ReglaNegocioException("Esta devolución ya no está pendiente.");
        }
        pagoRepository.findById(idPago).ifPresent(pago -> {
            pago.setActualizadoEn(LocalDateTime.now());
            pagoRepository.save(pago);
            log.info("Anticipo {} ({} centavos) devuelto a mano por el superadministrador {}.",
                    pago.getReferencia(), pago.getMontoCentavos(), idSuperadmin);
        });
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    private static List<String> idsDistintos(List<Pago> pagos, Function<Pago, String> id) {
        return pagos.stream().map(id).filter(Objects::nonNull).distinct().toList();
    }

    /** Cambia el estado de forma atómica y guarda los datos de la transacción. */
    private boolean marcar(Pago pago, EstadoPago esperado, EstadoPago nuevo, Transaccion transaccion) {
        if (pagoRepository.cambiarEstadoSi(pago.getId(), esperado, nuevo) == 0) {
            return false;
        }
        pago.setEstado(nuevo);
        pago.setIdTransaccion(transaccion.id());
        pago.setMetodo(transaccion.metodo());
        pago.setActualizadoEn(LocalDateTime.now());
        pagoRepository.save(pago);
        return true;
    }

    /** Única e impredecible: no debe poder adivinarse la referencia de otro pago. */
    private static String nuevaReferencia(String idReserva) {
        byte[] azar = new byte[8];
        ALEATORIO.nextBytes(azar);
        String cola = idReserva.length() > 6 ? idReserva.substring(idReserva.length() - 6) : idReserva;
        return "PKG-" + cola.toUpperCase() + "-" + HexFormat.of().withUpperCase().formatHex(azar);
    }

    static long aCentavos(Double pesos) {
        return BigDecimal.valueOf(pesos != null ? pesos : 0d)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
