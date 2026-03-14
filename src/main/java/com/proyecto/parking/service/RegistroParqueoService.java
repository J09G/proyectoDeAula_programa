package com.proyecto.parking.service;

import com.proyecto.parking.model.RegistroParqueo;
import java.util.List;

public interface RegistroParqueoService {

    RegistroParqueo registrarEntrada(String placa, String cedula, int idParqueadero);

    RegistroParqueo registrarSalida(int idRegistro);

    List<RegistroParqueo> listarActivosPorParqueadero(int idParqueadero);

    List<RegistroParqueo> listarTodosPorParqueadero(int idParqueadero);

    RegistroParqueo obtenerPorId(int idRegistro);
}
