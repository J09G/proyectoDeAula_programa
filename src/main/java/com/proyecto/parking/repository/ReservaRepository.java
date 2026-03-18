package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservaRepository extends MongoRepository<Reserva, String> {
    List<Reserva> findByParqueadero_Id(String idParqueadero, Sort sort);
    List<Reserva> findByCliente_Id(String idCliente, Sort sort);
    List<Reserva> findByCliente_IdAndParqueadero_Id(String idCliente, String idParqueadero);
    List<Reserva> findByCliente_IdAndParqueadero_IdAndEstado(String idCliente, String idParqueadero, EstadoReserva estado);
}
