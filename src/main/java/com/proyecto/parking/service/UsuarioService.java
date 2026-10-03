package com.proyecto.parking.service;

import com.proyecto.parking.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsuarioService {

    Usuario registrarUsuario(String nombre, String cedula, String correo,
                             String contrasena, String placa, String rolNombre);

    /**
     * Usuario con el que entra quien se autenticó con Google.
     *
     * <p>Si el correo ya existe (registrado con contraseña o por Google), se
     * devuelve esa misma cuenta: Google pasa a ser otra forma de entrar a ella.
     * Si no existe, se crea como cliente, sin contraseña ni cédula.</p>
     *
     * <p>Quien llama debe garantizar que Google verificó el correo: vincular por
     * un correo no verificado permitiría apoderarse de una cuenta ajena.</p>
     */
    Usuario obtenerOCrearUsuarioGoogle(String correo, String nombre);

    /**
     * Completa la cédula y la placa que le falten al usuario (típicamente, quien
     * entró con Google). Solo llena lo que está vacío: un dato que ya tiene no
     * se cambia por aquí, eso le corresponde al superadministrador.
     *
     * @throws com.proyecto.parking.exception.ReglaNegocioException
     *         si la cédula o la placa ya pertenecen a otro usuario
     */
    void completarPerfil(String idUsuario, String cedula, String placa);

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
