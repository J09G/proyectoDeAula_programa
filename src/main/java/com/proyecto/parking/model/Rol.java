package com.proyecto.parking.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Document(collection = "roles")
public class Rol {

    /** Nombres canónicos de los roles del sistema. */
    public static final String SUPERADMIN = "SuperAdmin";
    public static final String ADMINISTRADOR = "Administrador";
    public static final String CLIENTE = "Cliente";

    @MongoId(FieldType.OBJECT_ID)
    private String id;

    @Indexed(unique = true)
    private String nombre;

    public Rol() {}

    public Rol(String nombre) {
        this.nombre = nombre;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}
