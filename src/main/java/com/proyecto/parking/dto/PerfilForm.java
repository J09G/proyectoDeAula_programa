package com.proyecto.parking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Edición del propio perfil: nombre, correo y, opcionalmente, contraseña.
 *
 * <p>Los tres campos de contraseña van juntos o no van: si el usuario sólo
 * quiere cambiar su nombre, los deja vacíos. Las reglas cruzadas que lo
 * garantizan están abajo como {@code @AssertTrue}.</p>
 */
public class PerfilForm {

    @NotBlank(message = "{validacion.nombre.obligatorio}")
    @Size(min = 3, max = 80, message = "{validacion.nombre.longitud}")
    private String nombre;

    @NotBlank(message = "{validacion.correo.obligatorio}")
    @Email(message = "{validacion.correo.formato}")
    @Size(max = 120, message = "{validacion.correo.largo}")
    private String correo;

    private String passwordActual;
    private String passwordNueva;
    private String passwordConfirmar;

    /** @return true si el usuario está intentando cambiar la contraseña. */
    public boolean cambiaPassword() {
        return passwordNueva != null && !passwordNueva.isBlank();
    }

    /**
     * Sin la contraseña actual no se permite fijar una nueva: evita que alguien
     * que encuentre una sesión abierta se apropie de la cuenta.
     */
    @AssertTrue(message = "{validacion.password.actualObligatoria}")
    public boolean isPasswordActualPresente() {
        return !cambiaPassword() || (passwordActual != null && !passwordActual.isBlank());
    }

    @AssertTrue(message = "{validacion.password.longitud}")
    public boolean isPasswordNuevaValida() {
        return !cambiaPassword()
                || (passwordNueva.length() >= Validaciones.PASSWORD_MIN
                    && passwordNueva.length() <= Validaciones.PASSWORD_MAX);
    }

    @AssertTrue(message = "{validacion.password.noCoinciden}")
    public boolean isPasswordNuevaConfirmada() {
        return !cambiaPassword() || passwordNueva.equals(passwordConfirmar);
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPasswordActual() { return passwordActual; }
    public void setPasswordActual(String passwordActual) { this.passwordActual = passwordActual; }

    public String getPasswordNueva() { return passwordNueva; }
    public void setPasswordNueva(String passwordNueva) { this.passwordNueva = passwordNueva; }

    public String getPasswordConfirmar() { return passwordConfirmar; }
    public void setPasswordConfirmar(String passwordConfirmar) { this.passwordConfirmar = passwordConfirmar; }
}
