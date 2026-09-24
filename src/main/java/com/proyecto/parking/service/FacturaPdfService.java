package com.proyecto.parking.service;

import com.proyecto.parking.model.RegistroParqueo;

import java.io.OutputStream;

/** Generación del comprobante de pago en PDF. */
public interface FacturaPdfService {

    /**
     * Escribe la factura del registro en el flujo indicado.
     *
     * @param registro registro ya finalizado (con hora de salida y total)
     * @param destino  flujo de salida; quien llama se encarga de cerrarlo
     */
    void generar(RegistroParqueo registro, OutputStream destino);
}
