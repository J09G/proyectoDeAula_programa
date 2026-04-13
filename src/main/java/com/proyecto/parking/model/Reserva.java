package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "reservas")
public class Reserva {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private EstadoReserva estado;
    private Usuario cliente;
    private Parqueadero parqueadero;

    public enum EstadoReserva {
        PENDIENTE,
        ACEPTADA,
        RECHAZADA,
        UTILIZADA
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

    public Usuario getCliente() { return cliente; }
    public void setCliente(Usuario cliente) { this.cliente = cliente; }

    public Parqueadero getParqueadero() { return parqueadero; }
    public void setParqueadero(Parqueadero parqueadero) { this.parqueadero = parqueadero; }
}
