package com.proyecto.parking.service;

import com.proyecto.parking.model.Pago;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Anticipo que el cliente paga al reservar: garantiza que quien aparta un
 * cubículo tiene intención de llegar, y compensa al parqueadero si no lo hace.
 */
public interface PagoService {

    /** Cómo terminó la comprobación de un pago en la pasarela. */
    enum Resultado {
        /** Cobro confirmado: la reserva pasa a esperar la decisión del parqueadero. */
        APROBADO,
        /** La pasarela todavía lo está procesando (típico de PSE o Nequi). */
        EN_PROCESO,
        /** Rechazado o fallido: el cliente puede intentarlo otra vez. */
        RECHAZADO,
        /** Se cobró, pero la reserva ya no existía o había vencido: se devuelve el dinero. */
        APROBADO_SIN_RESERVA,
        /** No corresponde a ningún pago de ParkingApp. */
        NO_ENCONTRADO
    }

    /** false si la pasarela no está configurada: las reservas no cobran anticipo. */
    boolean pagosHabilitados();

    /** Anticipo de una reserva en ese parqueadero: su primera hora (o el mínimo de la pasarela). */
    double calcularAnticipo(Parqueadero parqueadero);

    /** Minutos de antelación con los que hay que cancelar para recuperar el anticipo. */
    long minutosAntelacionDevolucion();

    /** Hasta cuándo puede pagarse una reserva pendiente de pago antes de liberarse. */
    LocalDateTime limiteParaPagar(Reserva reserva);

    /**
     * Abre un intento de pago del anticipo y devuelve la dirección de la pasarela.
     *
     * @throws com.proyecto.parking.exception.RecursoNoEncontradoException
     *         si la reserva no existe o es de otro cliente
     * @throws com.proyecto.parking.exception.ReglaNegocioException
     *         si la reserva no está pendiente de pago o se venció el plazo
     */
    String iniciarPago(String idReserva, String idCliente);

    /** El cliente vuelve de la pasarela con el id de su transacción. */
    Resultado confirmarTransaccion(String idTransaccion);

    /** La pasarela avisa por su cuenta de un cambio en una transacción. Se ignora si no trae su firma. */
    void procesarAviso(PasarelaPago.Aviso aviso);

    /** Nombre de la pasarela, para los textos ("Pagar con ..."). */
    String nombrePasarela();

    /**
     * Pregunta a la pasarela por los intentos de una reserva que sigue sin
     * pagar, por si el cliente pagó y nunca volvió a la aplicación.
     *
     * @return true si alguno resultó aprobado y la reserva ya no está pendiente de pago
     */
    boolean recuperarPagoPerdido(Reserva reserva);

    /**
     * Devuelve al cliente el anticipo de una reserva (rechazo del parqueadero o
     * cancelación a tiempo). No hace nada si la reserva no tenía un pago aprobado.
     */
    void devolverAnticipo(Reserva reserva);

    /** El cliente canceló tarde o no llegó: el parqueadero se queda el anticipo. */
    void retenerAnticipo(Reserva reserva);

    /**
     * ¿Cancelar ahora daría derecho a la devolución del anticipo? Siempre, si el
     * parqueadero aún no la aceptó; si ya la aceptó, solo con la antelación configurada.
     */
    boolean cancelarDaDerechoADevolucion(Reserva reserva);

    /** Pago más relevante de cada reserva, para mostrar su estado en las listas. */
    Map<String, Pago> pagosPorReserva(Collection<String> idsReserva);

    Optional<Pago> pagoDeReserva(String idReserva);

    // ── Devoluciones manuales ────────────────────────────────────────────────

    /**
     * Un anticipo que la pasarela no pudo devolver sola (Nequi, PSE...), con los
     * datos que necesita el superadministrador para devolverlo a mano. El
     * cliente o el parqueadero pueden venir {@code null} si se eliminaron.
     */
    record DevolucionPendiente(Pago pago, Usuario cliente, Parqueadero parqueadero) {}

    /** Devoluciones que esperan a una persona, la más antigua primero. */
    List<DevolucionPendiente> listarDevolucionesPendientes();

    /**
     * El superadministrador ya le devolvió el dinero al cliente por fuera de la
     * aplicación (panel de la pasarela, transferencia...).
     *
     * @throws com.proyecto.parking.exception.ReglaNegocioException
     *         si ese pago no está pendiente de devolución (ya se marcó o no existe)
     */
    void marcarDevuelto(String idPago, String idSuperadmin);
}
