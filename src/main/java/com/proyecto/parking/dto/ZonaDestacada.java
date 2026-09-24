package com.proyecto.parking.dto;

import com.proyecto.parking.model.Zona;

/**
 * Una zona con el número de parqueaderos disponibles en ella, para las tarjetas
 * de la portada y del panel del cliente.
 */
public record ZonaDestacada(Zona zona, long parqueaderos) {

    /** Foto de la zona, por convención de nombre en /images. */
    public String imagen() {
        return "/images/" + zona.getNombreZona() + ".jpg";
    }
}
