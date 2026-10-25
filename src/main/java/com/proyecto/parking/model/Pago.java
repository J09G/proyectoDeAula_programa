package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

/**
 * Un intento de pago del anticipo de una reserva en la pasarela.
 *
 * <p>Cada vez que el cliente abre la página de pago se crea un {@code Pago}
 * nuevo con su propia referencia, porque la pasarela exige una referencia única
 * por transacción. Una reserva puede acumular varios intentos, pero como mucho
 * uno aprobado.</p>
 *
 * <p>Guarda los ids como texto (no como {@code @DBRef}) para que el registro del
 * dinero sobreviva aunque el administrador elimine la reserva.</p>
 */
@Document(collection = "pagos")
public class Pago {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    @Indexed
    private String idReserva;
    private String idCliente;
    private String idParqueadero;

    /** Referencia única que identifica este intento ante la pasarela. */
    @Indexed(unique = true)
    private String referencia;

    /** Importe en centavos, tal como lo maneja la pasarela (5.000 COP = 500000). */
    private long montoCentavos;
    private String moneda = "COP";

    private EstadoPago estado = EstadoPago.PENDIENTE;

    /** Id de la transacción en la pasarela; se conoce cuando el cliente paga. */
    private String idTransaccion;
    /** Medio usado (CARD, NEQUI, PSE...), informado por la pasarela. */
    private String metodo;

    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;

    public enum EstadoPago {
        /** Creado; el cliente aún no ha terminado de pagar. */
        PENDIENTE,
        /** La pasarela confirmó el cobro: el anticipo está a favor del cliente. */
        APROBADO,
        /** La pasarela lo rechazó o falló; el cliente puede intentarlo de nuevo. */
        RECHAZADO,
        /** Hay que devolverlo y la pasarela no pudo hacerlo sola: lo gestiona una persona. */
        DEVOLUCION_PENDIENTE,
        /** Devuelto al cliente. */
        DEVUELTO,
        /** El cliente canceló tarde o no llegó: el parqueadero se queda el anticipo. */
        RETENIDO;

        /** El dinero sigue en manos de ParkingApp a nombre del cliente. */
        public boolean esAFavorDelCliente() {
            return this == APROBADO;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdReserva() { return idReserva; }
    public void setIdReserva(String idReserva) { this.idReserva = idReserva; }

    public String getIdCliente() { return idCliente; }
    public void setIdCliente(String idCliente) { this.idCliente = idCliente; }

    public String getIdParqueadero() { return idParqueadero; }
    public void setIdParqueadero(String idParqueadero) { this.idParqueadero = idParqueadero; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public long getMontoCentavos() { return montoCentavos; }
    public void setMontoCentavos(long montoCentavos) { this.montoCentavos = montoCentavos; }

    /** Importe en pesos. */
    public double getMonto() { return montoCentavos / 100d; }

    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }

    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }

    public String getIdTransaccion() { return idTransaccion; }
    public void setIdTransaccion(String idTransaccion) { this.idTransaccion = idTransaccion; }

    public String getMetodo() { return metodo; }
    public void setMetodo(String metodo) { this.metodo = metodo; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime actualizadoEn) { this.actualizadoEn = actualizadoEn; }
}
