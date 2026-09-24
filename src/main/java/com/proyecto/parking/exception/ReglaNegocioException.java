package com.proyecto.parking.exception;

/**
 * Se lanza cuando la operación es sintácticamente válida pero viola una regla
 * del dominio (por ejemplo, reservar un espacio ya ocupado). Su mensaje está
 * redactado para mostrarse directamente al usuario final.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
