package com.proyecto.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta de una zona geográfica por el superadministrador. */
public class ZonaForm {

    @NotBlank(message = "{validacion.zona.nombreObligatorio}")
    @Size(min = 3, max = 60, message = "{validacion.zona.nombreLongitud}")
    private String nombreZona;

    public String getNombreZona() { return nombreZona; }
    public void setNombreZona(String nombreZona) { this.nombreZona = nombreZona; }
}
