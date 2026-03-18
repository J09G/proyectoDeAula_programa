package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ParqueaderoRepository extends MongoRepository<Parqueadero, String> {
    List<Parqueadero> findByZona_Id(String idZona);
    Parqueadero findByAdministrador_Id(String idAdministrador);
    Parqueadero findByRegistradoPor_Id(String idRegistradoPor);
    List<Parqueadero> findByNombreContainingIgnoreCase(String nombre);
    List<Parqueadero> findByAdministrador_CedulaContainingIgnoreCase(String cedula);
}
