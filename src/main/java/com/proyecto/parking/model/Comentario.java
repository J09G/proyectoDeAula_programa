package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.index.Indexed;
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

    /**
     * Puntuación de 1 a 5 estrellas.
     *
     * <p>La valoración que se muestra de un parqueadero es el promedio de estas
     * puntuaciones, no un número que el administrador escriba a mano: así no
     * puede inflarse su propia nota.</p>
     *
     * <p>Admite null porque los comentarios creados antes de existir este campo
     * no la tienen; el cálculo del promedio los ignora.</p>
     */
    private Integer puntuacion;

    @DBRef(lazy = true)
    private Usuario cliente;

    @Indexed
    @DBRef(lazy = true)
    private Parqueadero parqueadero;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public Integer getPuntuacion() { return puntuacion; }
    public void setPuntuacion(Integer puntuacion) { this.puntuacion = puntuacion; }

    public Usuario getCliente() { return cliente; }
    public void setCliente(Usuario cliente) { this.cliente = cliente; }

    public Parqueadero getParqueadero() { return parqueadero; }
    public void setParqueadero(Parqueadero parqueadero) { this.parqueadero = parqueadero; }
}
