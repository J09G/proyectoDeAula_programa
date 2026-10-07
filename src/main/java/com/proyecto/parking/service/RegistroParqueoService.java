package com.proyecto.parking.service;

import com.proyecto.parking.dto.EntradaForm;
import com.proyecto.parking.model.RegistroParqueo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RegistroParqueoService {

    RegistroParqueo registrarEntrada(String idParqueadero, String idAdministrador, EntradaForm form);

    RegistroParqueo registrarSalida(String idRegistro, String idAdministrador);

    List<RegistroParqueo> listarActivosPorParqueadero(String idParqueadero, String idAdministrador);

    Page<RegistroParqueo> listarHistorial(String idParqueadero, String idAdministrador, Pageable pageable);

    RegistroParqueo obtenerPorId(String idRegistro, String idAdministrador);

    /** Parqueos del cliente, del más reciente al más antiguo (US-12). */
    Page<RegistroParqueo> listarDelCliente(String idCliente, Pageable pageable);

    /**
     * Registro finalizado del propio cliente, para su factura. Si no existe,
     * es de otro cliente o sigue activo, responde igual: no encontrado (404).
     */
    RegistroParqueo obtenerFinalizadoDelCliente(String idRegistro, String idCliente);
}
