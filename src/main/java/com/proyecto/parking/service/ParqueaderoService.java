package com.proyecto.parking.service;

import com.proyecto.parking.dto.EditarParqueaderoForm;
import com.proyecto.parking.dto.ParqueaderoForm;
import com.proyecto.parking.model.Parqueadero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ParqueaderoService {

    /** Crea el parqueadero ya asignado a su administrador, en una sola escritura. */
    Parqueadero registrarParqueadero(ParqueaderoForm form, String idAdministrador);

    /**
     * Devuelve el parqueadero sólo si {@code idAdministrador} es su dueño.
     *
     * <p>Es la única puerta de entrada que deben usar las operaciones del panel
     * de administrador: sin esta comprobación bastaba con cambiar el id de la
     * URL para gestionar el parqueadero de otro administrador.</p>
     *
     * @throws com.proyecto.parking.exception.AccesoDenegadoException si no le pertenece
     */
    Parqueadero obtenerParqueaderoDeAdministrador(String idParqueadero, String idAdministrador);

    Parqueadero obtenerParqueaderoPorId(String idParqueadero);

    List<Parqueadero> obtenerParqueaderosPorAdministrador(String idAdministrador);

    List<Parqueadero> obtenerParqueaderosPorZona(String idZona);

    Page<Parqueadero> listarParqueaderos(Pageable pageable);

    Page<Parqueadero> buscarPorNombre(String nombre, Pageable pageable);

    Page<Parqueadero> buscarPorZona(String idZona, Pageable pageable);

    Page<Parqueadero> buscarPorCedulaAdmin(String cedula, Pageable pageable);

    void actualizarDatos(String idParqueadero, String idAdministrador, EditarParqueaderoForm form);

    void actualizarCapacidad(String idParqueadero, String idAdministrador,
                             int espaciosTotales, int espaciosDisponibles);

    void cambiarEstado(String idParqueadero, String idAdministrador, boolean habilitado);

    /** Variante para el superadministrador, que no es dueño de ningún parqueadero. */
    void cambiarEstadoComoSuperadmin(String idParqueadero, boolean habilitado);

    /**
     * Resta un cupo de forma atómica.
     *
     * @throws com.proyecto.parking.exception.ReglaNegocioException si no quedaban cupos
     */
    void ocuparEspacio(String idParqueadero);

    /** Devuelve un cupo de forma atómica. */
    void liberarEspacio(String idParqueadero);
}
