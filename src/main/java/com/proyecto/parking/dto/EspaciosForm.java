package com.proyecto.parking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Ajuste de la capacidad de un parqueadero. */
public class EspaciosForm {

    @NotNull(message = "{validacion.espaciosTotales.obligatorio}")
    @Min(value = 1, message = "{validacion.espaciosTotales.minimo}")
    @Max(value = 500, message = "{validacion.espaciosTotales.maximo}")
    private Integer espaciosTotales;

    @NotNull(message = "{validacion.espaciosDisponibles.obligatorio}")
    @Min(value = 0, message = "{validacion.espaciosDisponibles.minimo}")
    private Integer espaciosDisponibles;

    @AssertTrue(message = "{validacion.capacidad.coherente}")
    public boolean isCapacidadCoherente() {
        return espaciosTotales == null || espaciosDisponibles == null
                || espaciosDisponibles <= espaciosTotales;
    }

    public Integer getEspaciosTotales() { return espaciosTotales; }
    public void setEspaciosTotales(Integer espaciosTotales) { this.espaciosTotales = espaciosTotales; }

    public Integer getEspaciosDisponibles() { return espaciosDisponibles; }
    public void setEspaciosDisponibles(Integer espaciosDisponibles) { this.espaciosDisponibles = espaciosDisponibles; }
}
