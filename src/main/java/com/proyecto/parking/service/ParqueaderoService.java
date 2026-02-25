package com.proyecto.parking.service;

import com.proyecto.parking.model.Parqueadero;
import java.util.List;

public interface ParqueaderoService {

    Parqueadero registrarParqueadero(String nombre, String direccion, String horario,
                                     double tarifa, int espaciosTotales, int espaciosDisponibles,
                                     int idZona, int registradoPor, String urlMaps, String telefono); // ← agregado telefono

    Parqueadero actualizarParqueadero(int idParqueadero, String nombre, String direccion,
                                      String horario, double tarifa, int espaciosTotales,
                                      int espaciosDisponibles, int idZona, String urlMaps);

    Parqueadero obtenerParqueaderoPorAdministrador(int idUsuario);

    void eliminarParqueaderoPorAdministrador(int idUsuario);

    List<Parqueadero> obtenerParqueaderosPorZona(int idZona);

    Parqueadero obtenerParqueaderoPorId(int idParqueadero);

    List<Parqueadero> listarParqueaderos();

    void asignarAdministrador(int idParqueadero, int idAdministrador);

    void cambiarEstado(int idParqueadero, boolean habilitado);

    Parqueadero guardarParqueadero(Parqueadero parqueadero);

    List<Parqueadero> buscarPorNombre(String nombre);

    List<Parqueadero> buscarPorZona(int idZona);

}
