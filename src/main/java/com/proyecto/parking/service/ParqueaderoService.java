package com.proyecto.parking.service;

import com.proyecto.parking.model.Parqueadero;
import java.util.List;

public interface ParqueaderoService {

    Parqueadero registrarParqueadero(String nombre, String direccion, String horario,
                                     double tarifa, int espaciosTotales, int espaciosDisponibles,
                                     String idZona, String registradoPor, String urlMaps, String telefono);

    Parqueadero actualizarParqueadero(String idParqueadero, String nombre, String direccion,
                                      String horario, double tarifa, int espaciosTotales,
                                      int espaciosDisponibles, String idZona, String urlMaps);

    Parqueadero obtenerParqueaderoPorAdministrador(String idUsuario);

    void eliminarParqueaderoPorAdministrador(String idUsuario);

    List<Parqueadero> obtenerParqueaderosPorZona(String idZona);

    Parqueadero obtenerParqueaderoPorId(String idParqueadero);

    List<Parqueadero> listarParqueaderos();

    void asignarAdministrador(String idParqueadero, String idAdministrador);

    void cambiarEstado(String idParqueadero, boolean habilitado);

    Parqueadero guardarParqueadero(Parqueadero parqueadero);

    List<Parqueadero> buscarPorNombre(String nombre);

    List<Parqueadero> buscarPorZona(String idZona);

    List<Parqueadero> buscarPorCedulaAdmin(String cedula);
}
