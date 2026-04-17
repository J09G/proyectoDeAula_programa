package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Comentario;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ComentarioRepository;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComentarioServiceImpl implements ComentarioService {

    @Autowired
    private ComentarioRepository comentarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Override
    public Comentario crearComentario(String idCliente, String idParqueadero, String texto) {
        Usuario cliente = usuarioService.obtenerUsuarioPorId(idCliente);
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

        Comentario comentario = new Comentario();
        comentario.setTexto(texto.trim());
        comentario.setFecha(LocalDateTime.now());
        comentario.setCliente(cliente);
        comentario.setParqueadero(parqueadero);

        return comentarioRepository.save(comentario);
    }

    @Override
    public List<Comentario> listarPorParqueadero(String idParqueadero) {
        return comentarioRepository.findByParqueadero_Id(
                idParqueadero, Sort.by(Sort.Direction.DESC, "_id"));
    }
}
