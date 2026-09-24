package com.proyecto.parking.service.impl;

import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioServiceImpl.class);

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final ParqueaderoRepository parqueaderoRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                              RolRepository rolRepository,
                              ParqueaderoRepository parqueaderoRepository,
                              PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.parqueaderoRepository = parqueaderoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Usuario registrarUsuario(String nombre, String cedula, String correo,
                                    String contrasena, String placa, String rolNombre) {

        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        String cedulaNormalizada = cedula.trim();
        String placaNormalizada = (placa == null || placa.isBlank())
                ? null
                : placa.trim().toUpperCase(Locale.ROOT);

        // Estas comprobaciones dan un mensaje claro al usuario; la garantía real
        // de unicidad la dan los índices únicos de Mongo, que también cubren el
        // caso de dos registros simultáneos con el mismo correo.
        if (usuarioRepository.existsByCorreo(correoNormalizado)) {
            throw new ReglaNegocioException("El correo ya está registrado.");
        }
        if (usuarioRepository.existsByCedula(cedulaNormalizada)) {
            throw new ReglaNegocioException("La cédula ya está registrada.");
        }
        if (placaNormalizada != null && usuarioRepository.existsByPlaca(placaNormalizada)) {
            throw new ReglaNegocioException("La placa ya está registrada por otro usuario.");
        }

        Rol rol = rolRepository.findByNombre(rolNombre)
                .orElseThrow(() -> new IllegalStateException("Rol no configurado: " + rolNombre));

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setCedula(cedulaNormalizada);
        usuario.setCorreo(correoNormalizado);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRol(rol);
        usuario.setPlaca(placaNormalizada);
        usuario.setHabilitado(true);

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Usuario {} registrado con rol {}.", correoNormalizado, rolNombre);
        return guardado;
    }

    @Override
    public Usuario obtenerUsuarioPorId(String idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", idUsuario));
    }

    @Override
    public Usuario obtenerUsuarioPorCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    @Override
    public Page<Usuario> obtenerTodosLosUsuarios(Pageable pageable) {
        return usuarioRepository.findAll(pageable);
    }

    @Override
    public Page<Usuario> buscarPorCedula(String cedula, Pageable pageable) {
        return usuarioRepository.findByCedulaContaining(cedula.trim(), pageable);
    }

    @Override
    public void actualizarUsuario(String idUsuario, String nombre, String correo, String cedula) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);

        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        String cedulaNormalizada = cedula.trim();

        if (!correoNormalizado.equals(usuario.getCorreo())
                && usuarioRepository.existsByCorreo(correoNormalizado)) {
            throw new ReglaNegocioException("Ese correo ya pertenece a otro usuario.");
        }
        if (!cedulaNormalizada.equals(usuario.getCedula())
                && usuarioRepository.existsByCedula(cedulaNormalizada)) {
            throw new ReglaNegocioException("Esa cédula ya pertenece a otro usuario.");
        }

        usuario.setNombre(nombre.trim());
        usuario.setCorreo(correoNormalizado);
        usuario.setCedula(cedulaNormalizada);
        usuarioRepository.save(usuario);

        log.info("Usuario {} actualizado.", idUsuario);
    }

    @Override
    public boolean actualizarPerfil(String idUsuario, String nombre, String correo,
                                    String passwordActual, String passwordNueva) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);
        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);

        if (!correoNormalizado.equals(usuario.getCorreo())
                && usuarioRepository.existsByCorreo(correoNormalizado)) {
            throw new ReglaNegocioException("Ese correo ya pertenece a otro usuario.");
        }

        usuario.setNombre(nombre.trim());
        usuario.setCorreo(correoNormalizado);

        boolean cambioPassword = passwordNueva != null && !passwordNueva.isBlank();
        if (cambioPassword) {
            // Se comprueba aquí y no en el formulario porque requiere el hash
            // guardado, que el DTO no conoce.
            if (!passwordEncoder.matches(passwordActual, usuario.getContrasena())) {
                throw new ReglaNegocioException("La contraseña actual no es correcta.");
            }
            usuario.setContrasena(passwordEncoder.encode(passwordNueva));
        }

        usuarioRepository.save(usuario);
        log.info("Perfil de {} actualizado{}.", idUsuario, cambioPassword ? " (con cambio de contraseña)" : "");
        return cambioPassword;
    }

    @Override
    public void cambiarEstadoUsuario(String idUsuario, boolean habilitado) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);

        if (Rol.SUPERADMIN.equalsIgnoreCase(usuario.getRol().getNombre())) {
            throw new ReglaNegocioException("No se puede deshabilitar al superadministrador.");
        }

        usuario.setHabilitado(habilitado);
        usuarioRepository.save(usuario);

        // Al bloquear a un administrador se arrastran sus parqueaderos, para que
        // no sigan aceptando reservas que nadie va a atender.
        if (Rol.ADMINISTRADOR.equalsIgnoreCase(usuario.getRol().getNombre())) {
            List<Parqueadero> parqueaderos = parqueaderoRepository.findByAdministrador_Id(idUsuario);
            parqueaderos.forEach(p -> p.setHabilitado(habilitado));
            parqueaderoRepository.saveAll(parqueaderos);
            log.info("{} parqueaderos de {} pasaron a {}.",
                    parqueaderos.size(), idUsuario, habilitado ? "habilitados" : "deshabilitados");
        }

        log.info("Usuario {} {}.", idUsuario, habilitado ? "habilitado" : "deshabilitado");
    }

    @Override
    public boolean existeCorreo(String correo) {
        return usuarioRepository.existsByCorreo(correo.trim().toLowerCase(Locale.ROOT));
    }
}
