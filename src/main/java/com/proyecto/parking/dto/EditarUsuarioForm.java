package com.proyecto.parking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edición de los datos básicos de un usuario por el superadministrador. */
public class EditarUsuarioForm {

    @NotBlank(message = "{validacion.usuario.obligatorio}")
    private String idUsuario;

    @NotBlank(message = "{validacion.nombre.obligatorio}")
    @Size(min = 3, max = 80, message = "{validacion.nombre.longitud}")
    private String nombre;

    @NotBlank(message = "{validacion.correo.obligatorio}")
    @Email(message = "{validacion.correo.formato}")
    private String correo;

    @NotBlank(message = "{validacion.cedula.obligatoria}")
    @Pattern(regexp = Validaciones.CEDULA, message = "{validacion.cedula.formato}")
    private String cedula;

    public String getIdUsuario() { return idUsuario; }
    public void setIdUsuario(String idUsuario) { this.idUsuario = idUsuario; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = cedula; }
}
