package com.proyecto.parking.service;

import com.proyecto.parking.model.Pago;

import java.util.Map;
import java.util.Optional;

/**
 * Lo que ParkingApp necesita de una pasarela de pagos. La implementación actual
 * es Wompi; el resto de la aplicación sólo conoce esta interfaz, así que cambiar
 * de proveedor no obliga a tocar las reglas de negocio.
 */
public interface PasarelaPago {

    /** Datos de una transacción tal como los informa la pasarela. */
    record Transaccion(String id, String referencia, String estado, long montoCentavos,
                       String moneda, String metodo) {

        public boolean aprobada() { return "APPROVED".equals(estado); }

        /** Sigue en curso: aún no se sabe si se aprobó o se rechazó. */
        public boolean pendiente() { return "PENDING".equals(estado); }
    }

    /** Dirección de la página de pago a la que se envía al cliente. */
    String urlDePago(Pago pago, String urlRetorno);

    /** Consulta una transacción por su id. Vacío si no existe o la pasarela no responde. */
    Optional<Transaccion> consultarTransaccion(String idTransaccion);

    /**
     * Busca la transacción de una referencia, para los pagos cuyo aviso nunca
     * llegó. Vacío si no hay ninguna o no se dispone de credenciales para preguntar.
     */
    Optional<Transaccion> consultarPorReferencia(String referencia);

    /**
     * Intenta devolver el dinero de una transacción aprobada.
     *
     * @return true si la pasarela confirmó la devolución; false si hay que hacerla a mano
     */
    boolean devolver(String idTransaccion);

    /** true si el aviso recibido trae la firma de la pasarela. */
    boolean avisoEsAutentico(Map<String, Object> aviso);
}
