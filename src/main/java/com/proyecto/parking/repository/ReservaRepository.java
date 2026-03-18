package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends MongoRepository<Reserva, String> {
    List<Reserva> findByParqueadero(Parqueadero parqueadero);
    List<Reserva> findByCliente(Usuario cliente);
    List<Reserva> findByClienteAndParqueadero(Usuario cliente, Parqueadero parqueadero);
    Optional<Reserva> findByClienteAndParqueaderoAndEstado(Usuario cliente, Parqueadero parqueadero, EstadoReserva estado);
}
