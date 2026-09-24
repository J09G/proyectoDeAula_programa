package com.proyecto.parking.service.impl;

import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.ZonaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZonaServiceImpl implements ZonaService {

    private static final Logger log = LoggerFactory.getLogger(ZonaServiceImpl.class);

    private final ZonaRepository zonaRepository;

    public ZonaServiceImpl(ZonaRepository zonaRepository) {
        this.zonaRepository = zonaRepository;
    }

    @Override
    public List<Zona> obtenerZonas() {
        return zonaRepository.findAll();
    }

    @Override
    public List<Zona> obtenerZonasHabilitadas() {
        return zonaRepository.findByHabilitado(true);
    }

    @Override
    public Zona obtenerZonaPorId(String idZona) {
        return zonaRepository.findById(idZona)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Zona", idZona));
    }

    @Override
    public Zona crearZona(String nombreZona) {
        String nombre = nombreZona.trim();

        if (zonaRepository.existsByNombreZonaIgnoreCase(nombre)) {
            throw new ReglaNegocioException("Ya existe una zona llamada '" + nombre + "'.");
        }

        Zona zona = new Zona();
        zona.setNombreZona(nombre);
        Zona guardada = zonaRepository.save(zona);

        log.info("Zona '{}' creada (id {}).", nombre, guardada.getId());
        return guardada;
    }

    @Override
    public void cambiarEstadoZona(String idZona, boolean habilitado) {
        Zona zona = obtenerZonaPorId(idZona);
        zona.setHabilitado(habilitado);
        zonaRepository.save(zona);
        log.info("Zona {} {}.", idZona, habilitado ? "habilitada" : "deshabilitada");
    }
}
