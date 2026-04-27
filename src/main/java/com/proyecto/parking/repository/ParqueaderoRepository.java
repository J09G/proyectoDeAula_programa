package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ParqueaderoRepository extends MongoRepository<Parqueadero, String> {

    List<Parqueadero> findByZona_Id(String idZona);

    List<Parqueadero> findByZona_IdAndHabilitado(String idZona, boolean habilitado);

    Parqueadero findByAdministrador_Id(String idAdministrador);

    List<Parqueadero> findByNombreContainingIgnoreCase(String nombre);

}
