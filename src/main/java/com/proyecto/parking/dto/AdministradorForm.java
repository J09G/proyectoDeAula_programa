package com.proyecto.parking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Alta de un administrador desde el panel del superadministrador. */
public class AdministradorForm {

    @NotBlank(message = "{validacion.nombre.obligatorio}")
    @Size(min = 3, max = 80, message = "{validacion.nombre.longitud}")
    private String nombre;

    @NotBlank(message = "{validacion.cedula.obligatoria}")
    @Pattern(regexp = Validaciones.CEDULA, message = "{validacion.cedula.formato}")
    private String cedula;

    @NotBlank(message = "{validacion.correo.obligatorio}")
    @Email(message = "{validacion.correo.formato}")
    @Size(max = 120, message = "{validacion.correo.largo}")
    private String correo;

    @NotBlank(message = "{validacion.password.obligatoria}")
    @Size(min = Validaciones.PASSWORD_MIN, max = Validaciones.PASSWORD_MAX,
          message = "{validacion.password.longitud}")
    private String contrasena;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = cedula; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
}
