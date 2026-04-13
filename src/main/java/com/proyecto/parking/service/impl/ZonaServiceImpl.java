package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.service.ZonaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ZonaServiceImpl implements ZonaService {

    @Autowired
    private ZonaRepository zonaRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

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
                .or(() -> zonaRepository.findAll().stream()
                        .filter(z -> idZona.equals(z.getId()))
                        .findFirst())
                .orElseThrow(() -> new RuntimeException("Zona no encontrada con ID: " + idZona));
    }

    @Override
    public Zona crearZona(String nombreZona) {
        Zona zona = new Zona();
        zona.setNombreZona(nombreZona.trim());
        return zonaRepository.save(zona);
    }

    @Override
    public void cambiarEstadoZona(String idZona, boolean habilitado) {
        Zona zona = obtenerZonaPorId(idZona);
        Document filter = new Document("nombreZona", zona.getNombreZona());
        Document updateDoc = new Document("$set", new Document("habilitado", habilitado));
        mongoTemplate.getDb().getCollection("zonas").updateOne(filter, updateDoc);
    }
}
