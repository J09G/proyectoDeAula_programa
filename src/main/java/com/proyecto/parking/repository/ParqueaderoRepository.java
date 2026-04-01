package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ParqueaderoRepository extends MongoRepository<Parqueadero, String> {

    @Query("{ 'zona._id': ?0 }")
    List<Parqueadero> findByZona_Id(String idZona);

    @Query("{ 'administrador._id': ?0 }")
    Parqueadero findByAdministrador_Id(String idAdministrador);

    @Query("{ 'registradoPor._id': ?0 }")
    Parqueadero findByRegistradoPor_Id(String idRegistradoPor);

    List<Parqueadero> findByNombreContainingIgnoreCase(String nombre);

    @Query("{ 'administrador.cedula': { $regex: ?0, $options: 'i' } }")
    List<Parqueadero> findByAdministrador_CedulaContainingIgnoreCase(String cedula);
}
