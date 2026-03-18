package com.proyecto.parking.service;

import com.proyecto.parking.model.RegistroParqueo;
import java.util.List;

public interface RegistroParqueoService {

    RegistroParqueo registrarEntrada(String placa, String cedula, String idParqueadero);

    RegistroParqueo registrarSalida(String idRegistro);

    List<RegistroParqueo> listarActivosPorParqueadero(String idParqueadero);

    List<RegistroParqueo> listarTodosPorParqueadero(String idParqueadero);

    RegistroParqueo obtenerPorId(String idRegistro);
}
