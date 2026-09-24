package com.proyecto.parking.controller;

import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import java.util.stream.Collectors;

/**
 * Traduce los errores de validación a un texto apto para un mensaje flash.
 *
 * <p>Se usa en los formularios que redirigen tras el POST y por tanto no pueden
 * repintar la vista con el {@code BindingResult} completo.</p>
 */
final class Errores {

    private Errores() {}

    /** Todos los mensajes de validación, separados por espacio. */
    static String resumen(BindingResult errores) {
        String texto = errores.getAllErrors().stream()
                .map(ObjectError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));

        return texto.isBlank() ? "Los datos enviados no son válidos." : texto;
    }
}
