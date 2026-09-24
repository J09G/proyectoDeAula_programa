package com.proyecto.parking.exception;

/**
 * Se lanza cuando un usuario autenticado intenta operar sobre un recurso que no
 * le pertenece. Se traduce a un HTTP 403.
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
