package com.proyecto.parking.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolución de la IP del cliente, para el bloqueo por intentos fallidos.
 *
 * <p>Detrás de un proxy (Render, nginx), {@code getRemoteAddr()} ya es la IP
 * real del cliente: con {@code server.forward-headers-strategy=native} Tomcat
 * la toma de {@code X-Forwarded-For} leyendo de derecha a izquierda y saltando
 * solo los proxies de confianza.</p>
 *
 * <p>Antes se usaba la PRIMERA entrada de {@code X-Forwarded-For}. Esa la
 * escribe el cliente (los proxies agregan al final), así que bastaba mandar
 * una IP inventada distinta en cada intento para no llegar nunca al bloqueo.</p>
 */
final class IpUtils {

    private IpUtils() {}

    static String obtenerIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
