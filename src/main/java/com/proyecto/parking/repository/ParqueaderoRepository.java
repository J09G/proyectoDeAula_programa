package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParqueaderoRepository extends MongoRepository<Parqueadero, String> {

    List<Parqueadero> findByZona_IdAndHabilitado(String idZona, boolean habilitado);

    /** Cuántos parqueaderos visibles tiene una zona, para las tarjetas de portada. */
    long countByZona_IdAndHabilitado(String idZona, boolean habilitado);

    List<Parqueadero> findByHabilitado(boolean habilitado, org.springframework.data.domain.Sort sort);

    List<Parqueadero> findByAdministrador_Id(String idAdministrador);

    Page<Parqueadero> findByAdministrador_Id(String idAdministrador, Pageable pageable);

    Page<Parqueadero> findByAdministrador_IdIn(List<String> adminIds, Pageable pageable);

    Page<Parqueadero> findByNombreContainingIgnoreCase(String nombre, Pageable pageable);

    Page<Parqueadero> findByZona_Id(String idZona, Pageable pageable);

    /**
     * Suma atómica sobre el contador de cupos. Evita la condición de carrera del
     * patrón leer-modificar-guardar cuando dos entradas coinciden en el tiempo.
     *
     * <p>{@code delta} se pasa ya con signo. No se usa la forma {@code -?1}
     * dentro del JSON: el parser de consultas de Spring Data entra en recursión
     * infinita con ella y la petición muere con un {@code StackOverflowError}.</p>
     */
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'espaciosDisponibles': ?1 } }")
    long ajustarEspaciosDisponibles(@Param("id") String id, @Param("delta") int delta);

    /**
     * Resta cupos sólo si quedan suficientes disponibles, en una única operación
     * atómica.
     *
     * @param minimo cuántos cupos deben quedar como mínimo para que se aplique
     * @param delta  variación a aplicar, negativa para restar
     * @return documentos modificados; 0 significa que el parqueadero estaba
     *         lleno y la operación que la invoca debe abortar
     */
    @Query("{ '_id': ?0, 'espaciosDisponibles': { '$gte': ?1 } }")
    @Update("{ '$inc': { 'espaciosDisponibles': ?2 } }")
    long reservarEspaciosSiHayDisponibles(@Param("id") String id,
                                          @Param("minimo") int minimo,
                                          @Param("delta") int delta);
}
