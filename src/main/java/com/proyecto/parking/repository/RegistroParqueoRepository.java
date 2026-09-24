package com.proyecto.parking.repository;

import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroParqueoRepository extends MongoRepository<RegistroParqueo, String> {

    List<RegistroParqueo> findByParqueadero_IdAndEstado(String idParqueadero, EstadoRegistro estado);

    Optional<RegistroParqueo> findByPlacaAndEstado(String placa, EstadoRegistro estado);

    /** Detecta si un cubículo concreto ya tiene un vehículo dentro. */
    Optional<RegistroParqueo> findByParqueadero_IdAndEspacioReservadoAndEstado(
            String idParqueadero, Integer espacioReservado, EstadoRegistro estado);

    List<RegistroParqueo> findByUsuario_IdAndEstado(String idUsuario, EstadoRegistro estado);

    List<RegistroParqueo> findByParqueadero_Id(String idParqueadero, Sort sort);

    Page<RegistroParqueo> findByParqueadero_Id(String idParqueadero, Pageable pageable);
}
