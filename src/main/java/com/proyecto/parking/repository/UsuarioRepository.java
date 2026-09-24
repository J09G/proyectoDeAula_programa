package com.proyecto.parking.repository;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    boolean existsByCorreo(String correo);

    boolean existsByCedula(String cedula);

    boolean existsByPlaca(String placa);

    /**
     * Indica si ya existe algún usuario con ese rol.
     *
     * <p>Recibe el {@link Rol} y no su nombre a propósito: {@code rol} es un
     * {@code @DBRef} y MongoDB no sabe filtrar por una propiedad interna de una
     * referencia. Un {@code existsByRol_Nombre} compila pero falla en ejecución
     * con "Associations can only be pointed to directly or via their id".</p>
     */
    boolean existsByRol(Rol rol);

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByCedula(String cedula);

    Optional<Usuario> findByPlaca(String placa);

    List<Usuario> findByCedulaContaining(String cedula);

    Page<Usuario> findByCedulaContaining(String cedula, Pageable pageable);

    Page<Usuario> findAll(Pageable pageable);
}
