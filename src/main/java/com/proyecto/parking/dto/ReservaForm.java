package com.proyecto.parking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** Solicitud de reserva de un cubículo por parte de un cliente. */
public class ReservaForm {

    @NotBlank(message = "{validacion.parqueadero.obligatorio}")
    private String idParqueadero;

    @NotNull(message = "{validacion.fecha.obligatoria}")
    @Future(message = "{validacion.fecha.futura}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fechaReserva;

    @NotNull(message = "{validacion.cubiculo.obligatorio}")
    @Min(value = 1, message = "{validacion.cubiculo.minimo}")
    private Integer espacioReservado;

    public String getIdParqueadero() { return idParqueadero; }
    public void setIdParqueadero(String idParqueadero) { this.idParqueadero = idParqueadero; }

    public LocalDateTime getFechaReserva() { return fechaReserva; }
    public void setFechaReserva(LocalDateTime fechaReserva) { this.fechaReserva = fechaReserva; }

    public Integer getEspacioReservado() { return espacioReservado; }
    public void setEspacioReservado(Integer espacioReservado) { this.espacioReservado = espacioReservado; }
}
