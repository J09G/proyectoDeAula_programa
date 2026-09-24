package com.proyecto.parking.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolución de la IP del cliente.
 *
 * <p>La app corre detrás de nginx, así que {@code request.getRemoteAddr()} sería
 * siempre la IP del proxy y el bloqueo por intentos fallidos afectaría a todo el
 * mundo a la vez. Se usa la primera entrada de {@code X-Forwarded-For}, que es
 * la que añade nuestro propio proxy.</p>
 */
final class IpUtils {

    private IpUtils() {}

    static String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int coma = forwarded.indexOf(',');
            String primera = (coma > 0 ? forwarded.substring(0, coma) : forwarded).trim();
            if (!primera.isEmpty()) {
                return primera;
            }
        }
        return request.getRemoteAddr();
    }
}
