package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Override
    public void registrarUsuario(String nombre, String cedula, String correo, String contrasena, Integer idRol) {
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new RuntimeException("El correo ya está registrado");
        }
        if (usuarioRepository.existsByCedula(cedula)) {
            throw new RuntimeException("La cédula ya está registrada");
        }

        Rol rol = rolRepository.findById(idRol)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setCedula(cedula);
        usuario.setCorreo(correo);
        usuario.setContrasena(contrasena);
        usuario.setRol(rol);

        usuarioRepository.save(usuario);
    }

    public void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String placa, Integer idRol) {
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new RuntimeException("El correo ya está registrado");
        }
        if (usuarioRepository.existsByCedula(cedula)) {
            throw new RuntimeException("La cédula ya está registrada");
        }

        Rol rol = rolRepository.findById(idRol)
                .orElseThrow(() -> new RuntimeException("Rol no encontrado"));

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setCedula(cedula);
        usuario.setCorreo(correo);
        usuario.setContrasena(contrasena);
        usuario.setRol(rol);

        if (placa != null && !placa.trim().isEmpty()) {
            usuario.setPlaca(placa.trim());
        }

        usuarioRepository.save(usuario);
    }

    @Override
    public String validarLogin(String correo, String contrasena) {
        Usuario usuario = usuarioRepository.findByCorreo(correo);

        if (usuario != null && usuario.getContrasena().equals(contrasena)) {
            return usuario.getRol().getNombre(); 
        }
        return null;
    }

    @Override
    public Usuario obtenerUsuarioPorCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo);
    }

    @Override
    public List<Usuario> obtenerTodosLosUsuarios() {
        return usuarioRepository.findAll();
    }

    @Override
    public Usuario obtenerUsuarioPorId(int idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    public void actualizarUsuario(int idUsuario, String nombre, String correo, String cedula) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setCedula(cedula);

        usuarioRepository.save(usuario);
    }

    @Override
    public void eliminarUsuarioPorId(int idUsuario) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);

        String nombreRol = usuario.getRol().getNombre();

        if ("SuperAdmin".equalsIgnoreCase(nombreRol)) {
            System.out.println("No se puede eliminar al SuperAdministrador.");
            return;
        }

        usuarioRepository.delete(usuario);
        System.out.println("Usuario eliminado correctamente: " + idUsuario);
    }

    @Override
    public boolean existeCorreo(String correo) {
        return usuarioRepository.existsByCorreo(correo);
    }

    @Override
    public List<Usuario> buscarPorCedula(String cedula) {
        return usuarioRepository.findByCedulaContaining(cedula);
    }

}
