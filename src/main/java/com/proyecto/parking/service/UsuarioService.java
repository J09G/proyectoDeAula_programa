package com.proyecto.parking.service;

import com.proyecto.parking.model.Usuario;
import java.util.List;

public interface UsuarioService {

    void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String placa, String rolNombre);

    void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String rolNombre);

    String validarLogin(String correo, String contrasena);

    Usuario obtenerUsuarioPorCorreo(String correo);

    List<Usuario> obtenerTodosLosUsuarios();

    Usuario obtenerUsuarioPorId(String idUsuario);

    void actualizarUsuario(String idUsuario, String nombre, String correo, String cedula);

    void cambiarEstadoUsuario(String idUsuario, boolean habilitado);

    boolean existeCorreo(String correo);

    List<Usuario> buscarPorCedula(String cedula);
}
