package com.proyecto.parking.service;

import com.proyecto.parking.model.Usuario;
import java.util.List;

public interface UsuarioService {

    void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String placa, Integer idRol);

    void registrarUsuario(String nombre, String cedula, String correo, String contrasena, Integer idRol);

    String validarLogin(String correo, String contrasena);

    Usuario obtenerUsuarioPorCorreo(String correo);

    List<Usuario> obtenerTodosLosUsuarios();

    Usuario obtenerUsuarioPorId(int idUsuario);

    void actualizarUsuario(int idUsuario, String nombre, String correo, String cedula);

    void eliminarUsuarioPorId(int idUsuario);

    boolean existeCorreo(String correo);

    List<Usuario> buscarPorCedula(String cedula);
}
