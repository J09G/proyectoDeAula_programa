package com.proyecto.parking.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Punto que el administrador marca en el mini-mapa de la configuración (US-15). */
public class UbicacionForm {

    @NotNull(message = "{validacion.ubicacion.obligatoria}")
    @DecimalMin(value = "-90", message = "{validacion.ubicacion.latitud}")
    @DecimalMax(value = "90", message = "{validacion.ubicacion.latitud}")
    private Double latitud;

    @NotNull(message = "{validacion.ubicacion.obligatoria}")
    @DecimalMin(value = "-180", message = "{validacion.ubicacion.longitud}")
    @DecimalMax(value = "180", message = "{validacion.ubicacion.longitud}")
    private Double longitud;

    public Double getLatitud() { return latitud; }
    public void setLatitud(Double latitud) { this.latitud = latitud; }

    public Double getLongitud() { return longitud; }
    public void setLongitud(Double longitud) { this.longitud = longitud; }
}
