package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import java.time.LocalDateTime;

@Document(collection = "comentarios")
public class Comentario {

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    private String texto;
    private LocalDateTime fecha;

    @DBRef(lazy = true)
    private Usuario cliente;

    @DBRef(lazy = true)
    private Parqueadero parqueadero;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public Usuario getCliente() { return cliente; }
    public void setCliente(Usuario cliente) { this.cliente = cliente; }

    public Parqueadero getParqueadero() { return parqueadero; }
    public void setParqueadero(Parqueadero parqueadero) { this.parqueadero = parqueadero; }
}
