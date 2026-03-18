package com.proyecto.parking.repository;

import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservaRepository extends MongoRepository<Reserva, String> {

    @Query("{ 'parqueadero._id': ?0 }")
    List<Reserva> findByParqueadero_Id(String idParqueadero, Sort sort);

    @Query("{ 'cliente._id': ?0 }")
    List<Reserva> findByCliente_Id(String idCliente, Sort sort);

    @Query("{ 'cliente._id': ?0, 'parqueadero._id': ?1 }")
    List<Reserva> findByCliente_IdAndParqueadero_Id(String idCliente, String idParqueadero);

    @Query("{ 'cliente._id': ?0, 'parqueadero._id': ?1, 'estado': ?2 }")
    List<Reserva> findByCliente_IdAndParqueadero_IdAndEstado(String idCliente, String idParqueadero, EstadoReserva estado);
}
