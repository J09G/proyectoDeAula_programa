package com.proyecto.parking.repository;

import com.proyecto.parking.model.Comentario;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ComentarioRepository extends MongoRepository<Comentario, String> {

    List<Comentario> findByParqueadero_Id(String idParqueadero, Sort sort);
}
