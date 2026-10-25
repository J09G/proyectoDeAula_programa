package com.proyecto.parking.service;

import com.proyecto.parking.model.Pago;

import java.util.Map;
import java.util.Optional;

/**
 * Lo que ParkingApp necesita de una pasarela de pagos. La implementación es
 * Mercado Pago; el resto de la aplicación sólo conoce esta interfaz, así que
 * cambiar de proveedor no obliga a tocar las reglas de negocio.
 */
public interface PasarelaPago {

    /** Datos de una transacción tal como los informa la pasarela. */
    record Transaccion(String id, String referencia, String estado, long montoCentavos,
                       String moneda, String metodo) {

        public boolean aprobada() { return "APPROVED".equals(estado); }

        /** Sigue en curso: aún no se sabe si se aprobó o se rechazó. */
        public boolean pendiente() { return "PENDING".equals(estado); }
    }

    /**
     * Lo que llega en un aviso de la pasarela. Cada proveedor pone la firma y el
     * id en un sitio distinto (Mercado Pago: cabeceras y URL), así que se
     * entrega todo y la implementación toma lo que necesita.
     *
     * @param cuerpo     JSON recibido (puede venir vacío)
     * @param cabeceras  cabeceras HTTP, con el nombre en minúsculas
     * @param parametros parámetros de la URL
     */
    record Aviso(Map<String, Object> cuerpo, Map<String, String> cabeceras, Map<String, String> parametros) {}

    /** Nombre que ve el cliente ("Pagar con ..."). */
    String nombre();

    /**
     * Dirección de la página de pago a la que se envía al cliente.
     *
     * @throws com.proyecto.parking.exception.ReglaNegocioException si la pasarela no responde
     */
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

    /**
     * Si el aviso trae la firma de la pasarela y habla de una transacción,
     * devuelve el id de esa transacción. Vacío si no se puede comprobar: en ese
     * caso el aviso se descarta.
     */
    Optional<String> transaccionDelAviso(Aviso aviso);
}
