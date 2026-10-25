package com.proyecto.parking.repository;

import com.proyecto.parking.model.Pago;
import com.proyecto.parking.model.Pago.EstadoPago;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends MongoRepository<Pago, String> {

    Optional<Pago> findByReferencia(String referencia);

    List<Pago> findByIdReserva(String idReserva);

    Optional<Pago> findFirstByIdReservaAndEstado(String idReserva, EstadoPago estado);

    List<Pago> findByIdReservaInAndEstadoIn(Collection<String> idsReserva, Collection<EstadoPago> estados);

    List<Pago> findByEstado(EstadoPago estado, Sort sort);

    /**
     * Cambia el estado solo si sigue siendo {@code estadoEsperado}, en una única
     * operación atómica. La confirmación de un pago puede llegar dos veces a la
     * vez (el cliente vuelve de la pasarela y, en paralelo, llega su aviso): así
     * sólo una de las dos lo da por aprobado.
     *
     * @return documentos modificados; 0 significa que otro proceso ya lo cambió
     */
    @Query("{ '_id': ?0, 'estado': ?1 }")
    @Update("{ '$set': { 'estado': ?2 } }")
    long cambiarEstadoSi(String id, EstadoPago estadoEsperado, EstadoPago nuevoEstado);
}
