package com.proyecto.parking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Comentario público de un cliente sobre un parqueadero, con su puntuación. */
public class ComentarioForm {

    @NotBlank(message = "{validacion.parqueadero.obligatorio}")
    private String idParqueadero;

    @NotBlank(message = "{validacion.comentario.obligatorio}")
    @Size(min = 3, max = 500, message = "{validacion.comentario.longitud}")
    private String texto;

    /** La valoración del parqueadero es el promedio de estas puntuaciones. */
    @NotNull(message = "{validacion.puntuacion.obligatoria}")
    @Min(value = 1, message = "{validacion.puntuacion.rango}")
    @Max(value = 5, message = "{validacion.puntuacion.rango}")
    private Integer puntuacion;

    public String getIdParqueadero() { return idParqueadero; }
    public void setIdParqueadero(String idParqueadero) { this.idParqueadero = idParqueadero; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    public Integer getPuntuacion() { return puntuacion; }
    public void setPuntuacion(Integer puntuacion) { this.puntuacion = puntuacion; }
}
