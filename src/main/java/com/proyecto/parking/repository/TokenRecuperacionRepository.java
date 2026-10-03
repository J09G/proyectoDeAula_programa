package com.proyecto.parking.repository;

import com.proyecto.parking.model.TokenRecuperacion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface TokenRecuperacionRepository extends MongoRepository<TokenRecuperacion, String> {

    Optional<TokenRecuperacion> findByHashToken(String hashToken);

    /**
     * Busca y borra en una sola operación atómica (findAndRemove de Mongo): si
     * el mismo enlace se usa dos veces a la vez, solo una de las dos lo obtiene.
     */
    TokenRecuperacion deleteByHashToken(String hashToken);

    /** Hay un enlace pedido para este usuario después de {@code desde} (límite de envío). */
    boolean existsByIdUsuarioAndCreadoEnAfter(String idUsuario, Instant desde);

    /** Un enlace nuevo anula los anteriores; un restablecimiento los anula todos. */
    void deleteByIdUsuario(String idUsuario);
}
