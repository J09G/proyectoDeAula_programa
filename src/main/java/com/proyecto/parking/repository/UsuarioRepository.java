package com.proyecto.parking.repository;

import com.proyecto.parking.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    boolean existsByCorreo(String correo);

    boolean existsByCedula(String cedula);

    Usuario findByCorreo(String correo);
    
    Usuario findByCedula(String cedula);

    List<Usuario> findByCedulaContaining(String cedula);
}
