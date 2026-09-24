package com.proyecto.parking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Registro de entrada de un vehículo, hecho por el administrador en portería. */
public class EntradaForm {

    @NotBlank(message = "{validacion.placa.obligatoria}")
    @Pattern(regexp = Validaciones.PLACA, message = "{validacion.placa.formato}")
    private String placa;

    @NotNull(message = "{validacion.cubiculo.obligatorio}")
    @Min(value = 1, message = "{validacion.cubiculo.minimo}")
    private Integer espacioReservado;

    /** Opcional: si viene, se asocia el registro al cliente y a su reserva. */
    @Pattern(regexp = Validaciones.CEDULA, message = "{validacion.cedula.formato}")
    private String cedula;

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) {
        this.placa = (placa == null || placa.isBlank()) ? null : placa.trim().toUpperCase();
    }

    public Integer getEspacioReservado() { return espacioReservado; }
    public void setEspacioReservado(Integer espacioReservado) { this.espacioReservado = espacioReservado; }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) {
        this.cedula = (cedula == null || cedula.isBlank()) ? null : cedula.trim();
    }
}
