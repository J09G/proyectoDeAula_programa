package com.proyecto.parking.repository;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ParqueaderoRepository extends JpaRepository<Parqueadero, Integer> {

    List<Parqueadero> findByZona(Zona zona);

    Parqueadero findByRegistradoPor(Usuario usuario);
    
    Parqueadero findByAdministrador(Usuario administrador);

    List<Parqueadero> findByZona_IdZona(int idZona);

    List<Parqueadero> findByNombreContainingIgnoreCase(String nombre);

}
