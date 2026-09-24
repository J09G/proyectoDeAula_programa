package com.proyecto.parking.service.impl;

import com.proyecto.parking.dto.ParqueaderoDestacado;
import com.proyecto.parking.dto.Valoracion;
import com.proyecto.parking.dto.ZonaDestacada;
import com.proyecto.parking.model.Comentario;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ComentarioRepository;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.PortadaService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PortadaServiceImpl implements PortadaService {

    private final ZonaRepository zonaRepository;
    private final ParqueaderoRepository parqueaderoRepository;
    private final ComentarioRepository comentarioRepository;

    public PortadaServiceImpl(ZonaRepository zonaRepository,
                              ParqueaderoRepository parqueaderoRepository,
                              ComentarioRepository comentarioRepository) {
        this.zonaRepository = zonaRepository;
        this.parqueaderoRepository = parqueaderoRepository;
        this.comentarioRepository = comentarioRepository;
    }

    @Override
    public List<ZonaDestacada> zonasConConteo() {
        List<Zona> zonas = zonaRepository.findByHabilitado(true);

        return zonas.stream()
                .map(z -> new ZonaDestacada(z,
                        parqueaderoRepository.countByZona_IdAndHabilitado(z.getId(), true)))
                // Las zonas con más oferta primero: son las que más sirven a quien llega.
                .sorted(Comparator.comparingLong(ZonaDestacada::parqueaderos).reversed())
                .toList();
    }

    @Override
    public List<ParqueaderoDestacado> destacados(int limite) {
        List<Parqueadero> habilitados =
                parqueaderoRepository.findByHabilitado(true, Sort.by(Sort.Direction.DESC, "_id"));

        List<ParqueaderoDestacado> conValoracion = combinarConValoraciones(habilitados);

        return conValoracion.stream()
                // Primero los que tienen sitio; entre esos, los mejor valorados.
                .sorted(Comparator
                        .comparing((ParqueaderoDestacado d) -> tieneSitio(d.parqueadero())).reversed()
                        .thenComparing(d -> puntuacionParaOrdenar(d.valoracion()), Comparator.reverseOrder()))
                .limit(limite)
                .toList();
    }

    @Override
    public List<ParqueaderoDestacado> porZona(String idZona) {
        return combinarConValoraciones(
                parqueaderoRepository.findByZona_IdAndHabilitado(idZona, true));
    }

    @Override
    public Valoracion valoracionDe(String idParqueadero) {
        List<Comentario> comentarios =
                comentarioRepository.findByParqueadero_Id(idParqueadero, Sort.unsorted());
        return calcular(comentarios);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /**
     * Trae de una sola consulta los comentarios de todos los parqueaderos de la
     * lista y los reparte. Pedirlos uno a uno dentro del bucle daría una
     * consulta por tarjeta.
     */
    private List<ParqueaderoDestacado> combinarConValoraciones(List<Parqueadero> parqueaderos) {
        if (parqueaderos.isEmpty()) {
            return List.of();
        }

        Map<String, List<Comentario>> porParqueadero =
                comentarioRepository.findByParqueaderoIn(parqueaderos).stream()
                        .filter(c -> c.getParqueadero() != null)
                        .collect(Collectors.groupingBy(c -> c.getParqueadero().getId()));

        return parqueaderos.stream()
                .map(p -> new ParqueaderoDestacado(p,
                        calcular(porParqueadero.getOrDefault(p.getId(), List.of()))))
                .toList();
    }

    /** Promedio de las puntuaciones, ignorando los comentarios sin estrellas. */
    private Valoracion calcular(List<Comentario> comentarios) {
        List<Integer> puntuaciones = comentarios.stream()
                .map(Comentario::getPuntuacion)
                .filter(Objects::nonNull)
                .toList();

        if (puntuaciones.isEmpty()) {
            return Valoracion.sinValoraciones();
        }

        double promedio = puntuaciones.stream().mapToInt(Integer::intValue).average().orElse(0);
        return new Valoracion(promedio, puntuaciones.size());
    }

    private boolean tieneSitio(Parqueadero p) {
        return p.getEspaciosDisponibles() != null && p.getEspaciosDisponibles() > 0;
    }

    /** Un parqueadero sin valorar se ordena por debajo de cualquiera valorado. */
    private double puntuacionParaOrdenar(Valoracion v) {
        return v.tieneValoraciones() ? v.promedio() : -1;
    }
}
