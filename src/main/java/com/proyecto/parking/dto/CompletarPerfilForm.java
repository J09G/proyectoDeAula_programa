package com.proyecto.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cédula y placa que le faltan a un cliente (típicamente, quien entró con
 * Google). Mismas reglas que {@link RegistroClienteForm}.
 */
public class CompletarPerfilForm {

    @NotBlank(message = "{validacion.cedula.obligatoria}")
    @Pattern(regexp = Validaciones.CEDULA, message = "{validacion.cedula.formato}")
    private String cedula;

    @NotBlank(message = "{validacion.placa.obligatoria}")
    @Pattern(regexp = Validaciones.PLACA, message = "{validacion.placa.formato}")
    private String placa;

    /** Página a la que iba el cliente cuando se le pidió completar el perfil. */
    private String continuar;

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = cedula; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public String getContinuar() { return continuar; }
    public void setContinuar(String continuar) { this.continuar = continuar; }
}
