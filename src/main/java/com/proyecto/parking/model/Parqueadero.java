package com.proyecto.parking.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "parqueaderos")
public class Parqueadero {

    @Id
    private String id;

    private String nombre;
    private String direccion;
    private String horario;
    private Double tarifaHora;
    private Integer espaciosTotales;
    private Integer espaciosDisponibles;
    private String urlMaps;
    private String telefono;
    private Boolean habilitado = true;
    private Zona zona;
    private Usuario registradoPor;
    private Usuario administrador;

    public Parqueadero() {}

    public Parqueadero(String nombre, String direccion, String horario,
                       Double tarifaHora, Integer espaciosTotales, Integer espaciosDisponibles,
                       String urlMaps, String telefono, Boolean habilitado,
                       Zona zona, Usuario registradoPor, Usuario administrador) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.horario = horario;
        this.tarifaHora = tarifaHora;
        this.espaciosTotales = espaciosTotales;
        this.espaciosDisponibles = espaciosDisponibles;
        this.urlMaps = urlMaps;
        this.telefono = telefono;
        this.habilitado = habilitado;
        this.zona = zona;
        this.registradoPor = registradoPor;
        this.administrador = administrador;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public Double getTarifaHora() { return tarifaHora; }
    public void setTarifaHora(Double tarifaHora) { this.tarifaHora = tarifaHora; }

    public Integer getEspaciosTotales() { return espaciosTotales; }
    public void setEspaciosTotales(Integer espaciosTotales) { this.espaciosTotales = espaciosTotales; }

    public Integer getEspaciosDisponibles() { return espaciosDisponibles; }
    public void setEspaciosDisponibles(Integer espaciosDisponibles) { this.espaciosDisponibles = espaciosDisponibles; }

    public String getUrlMaps() { return urlMaps; }
    public void setUrlMaps(String urlMaps) { this.urlMaps = urlMaps; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public Boolean getHabilitado() { return habilitado; }
    public void setHabilitado(Boolean habilitado) { this.habilitado = habilitado; }

    public Zona getZona() { return zona; }
    public void setZona(Zona zona) { this.zona = zona; }

    public Usuario getRegistradoPor() { return registradoPor; }
    public void setRegistradoPor(Usuario registradoPor) { this.registradoPor = registradoPor; }

    public Usuario getAdministrador() { return administrador; }
    public void setAdministrador(Usuario administrador) { this.administrador = administrador; }
}
