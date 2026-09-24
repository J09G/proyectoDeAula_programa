package com.proyecto.parking.service;

import com.proyecto.parking.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsuarioService {

    Usuario registrarUsuario(String nombre, String cedula, String correo,
                             String contrasena, String placa, String rolNombre);

    Usuario obtenerUsuarioPorId(String idUsuario);

    Usuario obtenerUsuarioPorCorreo(String correo);

    Page<Usuario> obtenerTodosLosUsuarios(Pageable pageable);

    Page<Usuario> buscarPorCedula(String cedula, Pageable pageable);

    void actualizarUsuario(String idUsuario, String nombre, String correo, String cedula);

    /**
     * Actualiza los datos que el propio usuario puede cambiar de sí mismo.
     *
     * <p>La contraseña es opcional: si {@code passwordNueva} viene vacía, sólo
     * se tocan nombre y correo. Si viene, se exige la contraseña actual.</p>
     *
     * @return {@code true} si se cambió la contraseña
     * @throws com.proyecto.parking.exception.ReglaNegocioException
     *         si el correo ya es de otro usuario o la contraseña actual no coincide
     */
    boolean actualizarPerfil(String idUsuario, String nombre, String correo,
                             String passwordActual, String passwordNueva);

    void cambiarEstadoUsuario(String idUsuario, boolean habilitado);

    boolean existeCorreo(String correo);
}
