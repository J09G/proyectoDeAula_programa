package com.proyecto.parking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contraseña nueva elegida desde el enlace de recuperación. Mismas reglas que el registro. */
public class RestablecerForm {

    private String token;

    @NotBlank(message = "{validacion.password.obligatoria}")
    @Size(min = Validaciones.PASSWORD_MIN, max = Validaciones.PASSWORD_MAX,
          message = "{validacion.password.longitud}")
    private String password;

    @NotBlank(message = "{validacion.password.confirmar}")
    private String confirmPassword;

    @AssertTrue(message = "{validacion.password.noCoinciden}")
    public boolean isPasswordsCoinciden() {
        return password != null && password.equals(confirmPassword);
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
