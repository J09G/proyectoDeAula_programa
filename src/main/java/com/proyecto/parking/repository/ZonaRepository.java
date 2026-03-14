package com.proyecto.parking.repository;

import com.proyecto.parking.model.Zona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ZonaRepository extends JpaRepository<Zona, Integer> {
    List<Zona> findByHabilitado(boolean habilitado);
}