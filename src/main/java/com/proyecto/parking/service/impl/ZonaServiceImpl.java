package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.ZonaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZonaServiceImpl implements ZonaService {

    @Autowired
    private ZonaRepository zonaRepository;

    @Override
    public List<Zona> obtenerZonas() {
        return zonaRepository.findAll();
    }

    @Override
    public Zona obtenerZonaPorId(int idZona) {
        return zonaRepository.findById(idZona)
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con ID: " + idZona));
    }
}
