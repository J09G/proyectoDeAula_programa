package com.proyecto.parking.repository;

import com.proyecto.parking.model.Comentario;
import com.proyecto.parking.model.Parqueadero;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComentarioRepository extends MongoRepository<Comentario, String> {

    List<Comentario> findByParqueadero_Id(String idParqueadero, Sort sort);

    /**
     * Comentarios de varios parqueaderos de una vez.
     *
     * <p>Recibe los {@link Parqueadero} y no sus ids porque {@code parqueadero}
     * es un {@code @DBRef}: MongoDB no sabe filtrar por una propiedad interna de
     * una referencia, sólo por la referencia entera.</p>
     *
     * <p>Sirve para calcular las valoraciones de una lista de tarjetas con una
     * sola consulta en vez de una por tarjeta.</p>
     */
    List<Comentario> findByParqueaderoIn(List<Parqueadero> parqueaderos);
}
