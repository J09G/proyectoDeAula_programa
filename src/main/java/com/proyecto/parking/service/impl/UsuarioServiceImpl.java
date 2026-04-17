package com.proyecto.parking.service.impl;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
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

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String rolNombre) {
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new RuntimeException("El correo ya está registrado");
        }
        if (usuarioRepository.existsByCedula(cedula)) {
            throw new RuntimeException("La cédula ya está registrada");
        }

        Rol rol = rolRepository.findByNombre(rolNombre);
        if (rol == null) throw new RuntimeException("Rol no encontrado: " + rolNombre);

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setCedula(cedula);
        usuario.setCorreo(correo);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRol(rol);

        usuarioRepository.save(usuario);
    }

    @Override
    public void registrarUsuario(String nombre, String cedula, String correo, String contrasena, String placa, String rolNombre) {
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new RuntimeException("El correo ya está registrado");
        }
        if (usuarioRepository.existsByCedula(cedula)) {
            throw new RuntimeException("La cédula ya está registrada");
        }

        Rol rol = rolRepository.findByNombre(rolNombre);
        if (rol == null) throw new RuntimeException("Rol no encontrado: " + rolNombre);

        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setCedula(cedula);
        usuario.setCorreo(correo);
        usuario.setContrasena(passwordEncoder.encode(contrasena));
        usuario.setRol(rol);

        if (placa != null && !placa.trim().isEmpty()) {
            usuario.setPlaca(placa.trim());
        }

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
                .or(() -> usuarioRepository.findAll().stream()
                        .filter(u -> idUsuario.equals(u.getId()))
                        .findFirst())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @Override
    public void actualizarUsuario(String idUsuario, String nombre, String correo, String cedula) {
        java.util.List<Document> orUsuario = new java.util.ArrayList<>();
        orUsuario.add(new Document("_id", idUsuario));
        try { orUsuario.add(new Document("_id", new ObjectId(idUsuario))); } catch (IllegalArgumentException ignored) {}

        mongoTemplate.getDb().getCollection("usuarios").updateOne(
                new Document("$or", orUsuario),
                new Document("$set", new Document("nombre", nombre)
                        .append("correo", correo)
                        .append("cedula", cedula))
        );
    }

    @Override
    public void cambiarEstadoUsuario(String idUsuario, boolean habilitado) {
        Usuario usuario = obtenerUsuarioPorId(idUsuario);

        if ("SuperAdmin".equalsIgnoreCase(usuario.getRol().getNombre())) {
            throw new RuntimeException("No se puede deshabilitar al SuperAdministrador.");
        }

        java.util.List<Document> orUsuario = new java.util.ArrayList<>();
        orUsuario.add(new Document("_id", idUsuario));
        try { orUsuario.add(new Document("_id", new ObjectId(idUsuario))); } catch (IllegalArgumentException ignored) {}
        mongoTemplate.getDb().getCollection("usuarios")
                .updateOne(new Document("$or", orUsuario), new Document("$set", new Document("habilitado", habilitado)));

        // Si es administrador, también cambia el estado de su parqueadero
        if ("Administrador".equalsIgnoreCase(usuario.getRol().getNombre())) {
            Parqueadero parqueadero = parqueaderoRepository.findByAdministrador_Id(idUsuario);
            if (parqueadero != null) {
                java.util.List<Document> orParq = new java.util.ArrayList<>();
                orParq.add(new Document("_id", parqueadero.getId()));
                try { orParq.add(new Document("_id", new ObjectId(parqueadero.getId()))); } catch (IllegalArgumentException ignored) {}
                mongoTemplate.getDb().getCollection("parqueaderos")
                        .updateOne(new Document("$or", orParq), new Document("$set", new Document("habilitado", habilitado)));
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
