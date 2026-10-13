package com.proyecto.parking.service.impl;

import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
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

    static final int LARGO_MAXIMO_MOTIVO = 200;

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

    @Override
    public void reportarComentario(String idComentario, String idAdministrador, String motivo) {
        Comentario comentario = obtener(idComentario);
        // Lanza AccesoDenegadoException si el parqueadero no es suyo.
        parqueaderoService.obtenerParqueaderoDeAdministrador(
                comentario.getParqueadero().getId(), idAdministrador);

        if (motivo == null || motivo.isBlank()) {
            throw new ReglaNegocioException("Indica por qué debería revisarse el comentario.");
        }
        if (comentario.isReportado()) {
            throw new ReglaNegocioException("Este comentario ya está en revisión.");
        }

        String limpio = motivo.trim();
        comentario.setReportado(true);
        comentario.setMotivoReporte(limpio.length() > LARGO_MAXIMO_MOTIVO
                ? limpio.substring(0, LARGO_MAXIMO_MOTIVO) : limpio);
        comentario.setReportadoEn(LocalDateTime.now());
        comentarioRepository.save(comentario);
        log.info("Comentario {} reportado por el administrador {}.", idComentario, idAdministrador);
    }

    @Override
    public List<Comentario> listarReportados() {
        return comentarioRepository.findByReportadoTrue(Sort.by(Sort.Direction.DESC, "reportadoEn"));
    }

    @Override
    public long contarReportados() {
        return comentarioRepository.countByReportadoTrue();
    }

    @Override
    public void eliminarComentario(String idComentario) {
        Comentario comentario = obtener(idComentario);
        comentarioRepository.delete(comentario);
        log.info("Comentario {} eliminado por el superadministrador.", idComentario);
    }

    @Override
    public void descartarReporte(String idComentario) {
        Comentario comentario = obtener(idComentario);
        comentario.setReportado(false);
        comentario.setMotivoReporte(null);
        comentario.setReportadoEn(null);
        comentarioRepository.save(comentario);
        log.info("Reporte del comentario {} descartado: sigue publicado.", idComentario);
    }

    private Comentario obtener(String idComentario) {
        return comentarioRepository.findById(idComentario)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Comentario", idComentario));
    }
}
