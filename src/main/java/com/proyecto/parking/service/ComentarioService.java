package com.proyecto.parking.service;

import com.proyecto.parking.model.Comentario;
import java.util.List;

public interface ComentarioService {

    Comentario crearComentario(String idCliente, String idParqueadero, String texto);

    List<Comentario> listarPorParqueadero(String idParqueadero);
}
