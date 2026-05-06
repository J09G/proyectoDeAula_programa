package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private ParqueaderoRepository parqueaderoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String rolNombre) {
        registrarUsuario(nombre, cedula, correo, contrasena, null, rolNombre);
    }

    @Override
    public void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String placa, String rolNombre) {
        if (usuarioRepository.existsByCorreo(correo))
            throw new RuntimeException("El correo ya está registrado");
        if (usuarioRepository.existsByCedula(cedula))
            throw new RuntimeException("La cédula ya está registrada");

        Rol rol = rolRepository.findByNombre(rolNombre);
        if (rol == null) throw new RuntimeException("Rol no encontrado: " + rolNombre);

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setCedula(cedula);
        usuario.setCorreo(correo);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRol(rol);
        if (placa != null && !placa.trim().isEmpty())
            usuario.setPlaca(placa.trim());

        usuarioRepository.save(usuario);
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
    public Usuario obtenerUsuarioPorId(String idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    public void actualizarUsuario(String idUsuario, String nombre, String correo, String cedula) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);
        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setCedula(cedula);
        usuarioRepository.save(usuario);
    }

    @Override
    public void cambiarEstadoUsuario(String idUsuario, boolean habilitado) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);

        if ("SuperAdmin".equalsIgnoreCase(usuario.getRol().getNombre())) {
            throw new RuntimeException("No se puede deshabilitar al SuperAdministrador.");
        }

        usuario.setHabilitado(habilitado);
        usuarioRepository.save(usuario);

        if ("Administrador".equalsIgnoreCase(usuario.getRol().getNombre())) {
            Parqueadero parqueadero = parqueaderoRepository.findByAdministrador_Id(idUsuario);
            if (parqueadero != null) {
                parqueadero.setHabilitado(habilitado);
                parqueaderoRepository.save(parqueadero);
            }
        }
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
