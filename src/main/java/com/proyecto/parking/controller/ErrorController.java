package com.proyecto.parking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Páginas de error accesibles por URL.
 *
 * <p>{@code /error/403} es el destino de {@code accessDeniedPage} en
 * SecurityConfig. El resto de errores los resuelve
 * {@link com.proyecto.parking.exception.GlobalExceptionHandler} o el manejador
 * por defecto de Spring Boot, que ya encuentra {@code error/404} y
 * {@code error/500} por convención de nombres.</p>
 */
@Controller
public class ErrorController {

    /**
     * Acepta cualquier método HTTP a propósito: {@code accessDeniedPage} reenvía
     * la petición original, así que un POST rechazado por CSRF llega aquí como
     * POST. Con un {@code @GetMapping} daba un 405 que acababa en un 500.
     */
    @RequestMapping("/error/403")
    public String accesoDenegado() {
        return "error/403";
    }
}
