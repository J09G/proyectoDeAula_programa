package com.proyecto.parking.repository;

import com.proyecto.parking.model.Zona;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ZonaRepository extends MongoRepository<Zona, String> {
    List<Zona> findByHabilitado(boolean habilitado);
}
