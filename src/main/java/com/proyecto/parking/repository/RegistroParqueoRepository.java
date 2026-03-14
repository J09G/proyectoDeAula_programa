package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegistroParqueoRepository extends JpaRepository<RegistroParqueo, Integer> {

    List<RegistroParqueo> findByParqueadero(Parqueadero parqueadero);

    List<RegistroParqueo> findByParqueaderoAndEstado(Parqueadero parqueadero, EstadoRegistro estado);

    Optional<RegistroParqueo> findByPlacaAndEstado(String placa, EstadoRegistro estado);
}
