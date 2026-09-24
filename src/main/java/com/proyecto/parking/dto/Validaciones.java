package com.proyecto.parking.dto;

/**
 * Expresiones regulares y límites compartidos por los formularios.
 * Centralizarlos evita que cada DTO invente su propia regla.
 */
public final class Validaciones {

    /** Entre 6 y 12 dígitos. */
    public static final String CEDULA = "^[0-9]{6,12}$";

    /** Placas tipo ABC123, ABC-123, ABC12D. */
    public static final String PLACA = "^[A-Za-z]{3}-?[0-9]{2,3}[A-Za-z]?$";

    /** Dígitos, espacios, guiones y un + inicial opcional. */
    public static final String TELEFONO = "^\\+?[0-9][0-9\\s-]{6,14}$";

    /** Sólo se aceptan enlaces http/https, para no permitir javascript: en un href. */
    public static final String URL_HTTP = "^https?://.+";

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 72; // límite real de BCrypt

    private Validaciones() {}
}
