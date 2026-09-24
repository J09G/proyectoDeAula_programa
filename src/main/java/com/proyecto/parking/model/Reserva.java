package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Document(collection = "reservas")
@CompoundIndexes({
        @CompoundIndex(name = "idx_parqueadero_estado", def = "{'parqueadero': 1, 'estado': 1}"),
        @CompoundIndex(name = "idx_cliente_estado", def = "{'cliente': 1, 'estado': 1}"),
        @CompoundIndex(name = "idx_parqueadero_espacio_estado",
                def = "{'parqueadero': 1, 'espacioReservado': 1, 'estado': 1}")
})
public class Reserva {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private EstadoReserva estado;
    private LocalDateTime fechaReserva;
    private Integer espacioReservado;

    @DBRef(lazy = true)
    private Usuario cliente;

    @DBRef(lazy = true)
    private Parqueadero parqueadero;

    public enum EstadoReserva {
        /** Creada por el cliente, a la espera de que el administrador decida. */
        PENDIENTE,
        /** Aceptada: el cubículo queda bloqueado para el cliente. */
        ACEPTADA,
        /** Rechazada por el administrador. */
        RECHAZADA,
        /** El cliente llegó y se convirtió en un registro de parqueo. */
        UTILIZADA,
        /** Aceptada pero el cliente nunca llegó; el cubículo fue liberado. */
        EXPIRADA;

        /** Estados que mantienen un cubículo bloqueado. */
        public boolean bloqueaEspacio() {
            return this == ACEPTADA;
        }

        /** Un estado final ya no admite transiciones. */
        public boolean esFinal() {
            return this == RECHAZADA || this == UTILIZADA || this == EXPIRADA;
        }
    }

    public Reserva() {}

    public Reserva(EstadoReserva estado, Usuario cliente, Parqueadero parqueadero) {
        this.estado = estado;
        this.cliente = cliente;
        this.parqueadero = parqueadero;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public EstadoReserva getEstado() { return estado; }
    public void setEstado(EstadoReserva estado) { this.estado = estado; }

    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDateTime fechaReserva) { this.fechaReserva = fechaReserva; }

    public Usuario getCliente() { return cliente; }
    public void setCliente(Usuario cliente) { this.cliente = cliente; }

    public Integer getEspacioReservado() { return espacioReservado; }
    public void setEspacioReservado(Integer espacioReservado) { this.espacioReservado = espacioReservado; }

    public Parqueadero getParqueadero() { return parqueadero; }
    public void setParqueadero(Parqueadero parqueadero) { this.parqueadero = parqueadero; }
}
