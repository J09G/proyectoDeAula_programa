package com.proyecto.parking.model;

import jakarta.persistence.*;

@Entity
@Table(name = "parqueadero")
public class Parqueadero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idParqueadero") 
    private Integer idParqueadero;

    @Column(nullable = false, length = 45)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String direccion;

    @Column(nullable = false, length = 100)
    private String horario;

    @Column(name = "tarifa_hora", nullable = false)
    private Double tarifaHora;

    @Column(name = "espacios_totales", nullable = false)
    private Integer espaciosTotales;

    @Column(name = "espacios_disponibles", nullable = false)
    private Integer espaciosDisponibles;

    @Column(name = "url_maps", length = 255)
    private String urlMaps;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(nullable = false)
    private Boolean habilitado = true;

    @ManyToOne
    @JoinColumn(name = "id_zona")
    private Zona zona;

    @ManyToOne
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @ManyToOne
    @JoinColumn(name = "id_administrador")
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

    // Getters y Setters
    public Integer getIdParqueadero() { return idParqueadero; }
    public void setIdParqueadero(Integer idParqueadero) { this.idParqueadero = idParqueadero; }

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
