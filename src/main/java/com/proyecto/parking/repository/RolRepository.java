package com.proyecto.parking.repository;

import com.proyecto.parking.model.Rol;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RolRepository extends MongoRepository<Rol, String> {

    Optional<Rol> findByNombre(String nombre);

    boolean existsByNombre(String nombre);
}
