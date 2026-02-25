package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Integer> {

    List<Reserva> findByParqueadero(Parqueadero parqueadero);

    List<Reserva> findByCliente(Usuario cliente);

    List<Reserva> findByClienteAndParqueadero(Usuario cliente, Parqueadero parqueadero);
}
