package com.proyecto.parking.service;

import com.proyecto.parking.model.Comentario;

import java.util.List;

public interface ComentarioService {

    Comentario crearComentario(String idCliente, String idParqueadero, String texto, Integer puntuacion);

    List<Comentario> listarPorParqueadero(String idParqueadero);

    /**
     * El administrador de un parqueadero pide que se revise uno de sus comentarios.
     *
     * @throws com.proyecto.parking.exception.AccesoDenegadoException si el
     *         comentario es de un parqueadero que no le pertenece
     */
    void reportarComentario(String idComentario, String idAdministrador, String motivo);

    /** Comentarios que esperan la decisión del superadministrador. */
    List<Comentario> listarReportados();

    long contarReportados();

    /** El superadministrador da la razón al reporte: el comentario desaparece. */
    void eliminarComentario(String idComentario);

    /** El superadministrador no ve motivo para borrarlo: se queda publicado. */
    void descartarReporte(String idComentario);
}
