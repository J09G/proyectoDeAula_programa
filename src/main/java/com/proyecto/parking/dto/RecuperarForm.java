package com.proyecto.parking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correo al que se pide el enlace de recuperación de contraseña. */
public class RecuperarForm {

    @NotBlank(message = "{validacion.correo.obligatorio}")
    @Email(message = "{validacion.correo.formato}")
    @Size(max = 120, message = "{validacion.correo.largo}")
    private String correo;

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
}
