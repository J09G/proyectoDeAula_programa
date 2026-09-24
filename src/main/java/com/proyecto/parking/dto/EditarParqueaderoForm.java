package com.proyecto.parking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edición de los datos descriptivos de un parqueadero (no toca la capacidad). */
public class EditarParqueaderoForm {

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

    @Pattern(regexp = Validaciones.URL_HTTP, message = "{validacion.urlMaps.formato}")
    private String urlMaps;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getHorario() { return horario; }
    public void setHorario(String horario) { this.horario = horario; }

    public Double getTarifa() { return tarifa; }
    public void setTarifa(Double tarifa) { this.tarifa = tarifa; }

    public String getUrlMaps() { return urlMaps; }
    public void setUrlMaps(String urlMaps) {
        this.urlMaps = (urlMaps == null || urlMaps.isBlank()) ? null : urlMaps.trim();
    }
}
