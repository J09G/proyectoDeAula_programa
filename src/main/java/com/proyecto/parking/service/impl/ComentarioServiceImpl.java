package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Comentario;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ComentarioRepository;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComentarioServiceImpl implements ComentarioService {

    private static final Logger log = LoggerFactory.getLogger(ComentarioServiceImpl.class);

    private final ComentarioRepository comentarioRepository;
    private final UsuarioService usuarioService;
    private final ParqueaderoService parqueaderoService;

    public ComentarioServiceImpl(ComentarioRepository comentarioRepository,
                                 UsuarioService usuarioService,
                                 ParqueaderoService parqueaderoService) {
        this.comentarioRepository = comentarioRepository;
        this.usuarioService = usuarioService;
        this.parqueaderoService = parqueaderoService;
    }

    @Override
    public Comentario crearComentario(String idCliente, String idParqueadero, String texto, Integer puntuacion) {
        Usuario cliente = usuarioService.obtenerUsuarioPorId(idCliente);
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

        Comentario comentario = new Comentario();
        comentario.setTexto(texto.trim());
        comentario.setPuntuacion(puntuacion);
        comentario.setFecha(LocalDateTime.now());
        comentario.setCliente(cliente);
        comentario.setParqueadero(parqueadero);

        Comentario guardado = comentarioRepository.save(comentario);
        log.debug("Comentario {} publicado por {} en el parqueadero {}.",
                guardado.getId(), idCliente, idParqueadero);
        return guardado;
    }

    @Override
    public List<Comentario> listarPorParqueadero(String idParqueadero) {
        return comentarioRepository.findByParqueadero_Id(
                idParqueadero, Sort.by(Sort.Direction.DESC, "_id"));
    }
}
