package com.proyecto.parking.exception;

/**
 * Se lanza cuando una entidad solicitada por id no existe en la base de datos.
 * Se traduce a un HTTP 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException de(String entidad, String id) {
        return new RecursoNoEncontradoException(entidad + " no encontrado (id: " + id + ").");
    }
}
