package com.proyecto.parking.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de un cliente desde el formulario público de registro. */
public class RegistroClienteForm {

    @NotBlank(message = "{validacion.nombre.obligatorio}")
    @Size(min = 3, max = 80, message = "{validacion.nombre.longitud}")
    private String nombre;

    @NotBlank(message = "{validacion.cedula.obligatoria}")
    @Pattern(regexp = Validaciones.CEDULA, message = "{validacion.cedula.formato}")
    private String cedula;

    @NotBlank(message = "{validacion.placa.obligatoria}")
    @Pattern(regexp = Validaciones.PLACA, message = "{validacion.placa.formato}")
    private String placa;

    @NotBlank(message = "{validacion.correo.obligatorio}")
    @Email(message = "{validacion.correo.formato}")
    @Size(max = 120, message = "{validacion.correo.largo}")
    private String email;

    @NotBlank(message = "{validacion.password.obligatoria}")
    @Size(min = Validaciones.PASSWORD_MIN, max = Validaciones.PASSWORD_MAX,
          message = "{validacion.password.longitud}")
    private String password;

    @NotBlank(message = "{validacion.password.confirmar}")
    private String confirmPassword;

    /**
     * Comprobación en servidor de que ambas contraseñas coinciden. El formulario
     * ya lo valida en el navegador, pero eso no es una garantía: cualquiera puede
     * enviar el POST directamente.
     */
    @AssertTrue(message = "{validacion.password.noCoinciden}")
    public boolean isPasswordsCoinciden() {
        return password != null && password.equals(confirmPassword);
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = cedula; }

    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
