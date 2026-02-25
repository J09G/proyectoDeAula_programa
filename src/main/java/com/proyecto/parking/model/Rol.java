package com.proyecto.parking.model;

import jakarta.persistence.*; 

@Entity
@Table(name = "rol")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idRol") 
    private Integer idRol;

    @Column(name = "nombre", nullable = false, length = 45)
    private String nombre; 

    public Rol() {}

    public Rol(String nombre) {
        this.nombre = nombre;
    }

    public Integer getIdRol() {
        return idRol;
    }

    public void setIdRol(Integer idRol) {
        this.idRol = idRol;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
