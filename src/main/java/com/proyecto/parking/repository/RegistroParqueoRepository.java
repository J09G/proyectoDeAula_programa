package com.proyecto.parking.repository;

import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroParqueoRepository extends MongoRepository<RegistroParqueo, String> {

    List<RegistroParqueo> findByParqueadero_Id(String idParqueadero);

    List<RegistroParqueo> findByParqueadero_IdAndEstado(String idParqueadero, EstadoRegistro estado);

    Optional<RegistroParqueo> findByPlacaAndEstado(String placa, EstadoRegistro estado);

    List<RegistroParqueo> findByUsuario_IdAndEstado(String idUsuario, EstadoRegistro estado);
}