package com.proyecto.parking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de un parqueadero por su administrador. */
public class ParqueaderoForm {

    @NotBlank(message = "{validacion.nombre.obligatorio}")
    @Size(min = 3, max = 100, message = "{validacion.nombre.longitud}")
    private String nombre;

    @NotBlank(message = "{validacion.direccion.obligatoria}")
    @Size(min = 5, max = 150, message = "{validacion.direccion.longitud}")
    private String direccion;

    @NotBlank(message = "{validacion.horario.obligatorio}")
    @Size(max = 80, message = "{validacion.horario.largo}")
    private String horario;

    @NotNull(message = "{validacion.tarifa.obligatoria}")
    @DecimalMin(value = "0.0", inclusive = false, message = "{validacion.tarifa.positiva}")
    private Double tarifa;

    @NotNull(message = "{validacion.espaciosTotales.obligatorio}")
    @Min(value = 1, message = "{validacion.espaciosTotales.minimo}")
    @Max(value = 500, message = "{validacion.espaciosTotales.maximo}")
    private Integer espaciosTotales;

    @NotNull(message = "{validacion.espaciosDisponibles.obligatorio}")
    @Min(value = 0, message = "{validacion.espaciosDisponibles.minimo}")
    private Integer espaciosDisponibles;

    @NotBlank(message = "{validacion.zona.obligatoria}")
    private String idZona;

    @Pattern(regexp = Validaciones.TELEFONO, message = "{validacion.telefono.formato}")
    private String telefono;

    @Pattern(regexp = Validaciones.URL_HTTP, message = "{validacion.urlMaps.formato}")
    private String urlMaps;

    @AssertTrue(message = "{validacion.capacidad.coherente}")
    public boolean isCapacidadCoherente() {
        return espaciosTotales == null || espaciosDisponibles == null
                || espaciosDisponibles <= espaciosTotales;
    }

    /** Convierte "" en null para que las validaciones de campos opcionales no salten. */
    private static String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public Double getTarifa() { return tarifa; }
    public void setTarifa(Double tarifa) { this.tarifa = tarifa; }

    public Integer getEspaciosTotales() { return espaciosTotales; }
    public void setEspaciosTotales(Integer espaciosTotales) { this.espaciosTotales = espaciosTotales; }

    public Integer getEspaciosDisponibles() { return espaciosDisponibles; }
    public void setEspaciosDisponibles(Integer espaciosDisponibles) { this.espaciosDisponibles = espaciosDisponibles; }

    public String getIdZona() { return idZona; }
    public void setIdZona(String idZona) { this.idZona = idZona; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = normalizar(telefono); }

    public String getUrlMaps() { return urlMaps; }
    public void setUrlMaps(String urlMaps) { this.urlMaps = normalizar(urlMaps); }
}
