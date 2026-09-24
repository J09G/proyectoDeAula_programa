package com.proyecto.parking.dto;

import com.proyecto.parking.model.Parqueadero;

/**
 * Un parqueadero junto con su valoración, tal y como lo necesitan las tarjetas
 * del cliente y de la portada.
 *
 * <p>Existe para que la vista no tenga que pedir los comentarios de cada
 * parqueadero por su cuenta: eso provocaría una consulta por tarjeta.</p>
 */
public record ParqueaderoDestacado(Parqueadero parqueadero, Valoracion valoracion) {

    /** Imagen de la tarjeta; si el parqueadero no tiene, se usa la de su zona. */
    public String imagen() {
        String propia = parqueadero.getUrlImagen();
        if (propia != null && !propia.isBlank()) {
            return propia;
        }
        return "/images/" + parqueadero.getZona().getNombreZona() + ".jpg";
    }
}
