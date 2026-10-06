package com.proyecto.parking.repository;

import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservaRepository extends MongoRepository<Reserva, String> {

    List<Reserva> findByParqueadero_Id(String idParqueadero, Sort sort);

    Page<Reserva> findByParqueadero_Id(String idParqueadero, Pageable pageable);

    Page<Reserva> findByCliente_IdAndParqueadero_Id(String idCliente, String idParqueadero, Pageable pageable);

    long countByParqueadero_IdAndEstado(String idParqueadero, EstadoReserva estado);

    List<Reserva> findByCliente_Id(String idCliente, Sort sort);

    List<Reserva> findByCliente_IdAndParqueadero_Id(String idCliente, String idParqueadero);

    List<Reserva> findByCliente_IdAndParqueadero_IdAndEstado(String idCliente, String idParqueadero, EstadoReserva estado);

    List<Reserva> findByCliente_IdAndEstadoIn(String idCliente, List<EstadoReserva> estados);

    List<Reserva> findByParqueadero_IdAndEspacioReservadoAndEstado(String idParqueadero, Integer espacioReservado, EstadoReserva estado);

    List<Reserva> findByParqueadero_IdAndEstado(String idParqueadero, EstadoReserva estado);

    /**
     * Cambia el estado solo si sigue siendo {@code estadoEsperado}, en una única
     * operación atómica. Evita que dos acciones simultáneas (el cliente cancela
     * mientras el administrador acepta) se pisen y dejen un cupo perdido.
     *
     * @return documentos modificados; 0 significa que el estado ya había cambiado
     */
    @Query("{ '_id': ?0, 'estado': ?1 }")
    @Update("{ '$set': { 'estado': ?2 } }")
    long cambiarEstadoSi(String id, EstadoReserva estadoEsperado, EstadoReserva nuevoEstado);

    /** Reservas aceptadas cuya hora de llegada ya pasó: candidatas a expirar. */
    List<Reserva> findByEstadoAndFechaReservaBefore(EstadoReserva estado, LocalDateTime limite);
}
