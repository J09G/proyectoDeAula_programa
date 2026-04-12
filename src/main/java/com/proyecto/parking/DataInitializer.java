package com.proyecto.parking;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (rolRepository.count() == 0) {
            rolRepository.save(new Rol("SuperAdmin"));
            rolRepository.save(new Rol("Administrador"));
            rolRepository.save(new Rol("Cliente"));

            Rol rolSuperAdmin = rolRepository.findByNombre("SuperAdmin");
            Usuario superAdmin = new Usuario();
            superAdmin.setNombre("Super Admin");
            superAdmin.setCedula("0000000000");
            superAdmin.setCorreo("superadmin@parking.com");
            superAdmin.setContrasena(passwordEncoder.encode("superadmin123"));
            superAdmin.setRol(rolSuperAdmin);
            superAdmin.setHabilitado(true);
            usuarioRepository.save(superAdmin);

            System.out.println("Datos iniciales creados.");
        }

        // Migrar contraseñas en texto plano a BCrypt
        List<Usuario> usuarios = usuarioRepository.findAll();
        for (Usuario usuario : usuarios) {
            String contrasena = usuario.getContrasena();
            if (contrasena != null && !contrasena.startsWith("$2a$")) {
                usuario.setContrasena(passwordEncoder.encode(contrasena));
                usuarioRepository.save(usuario);
            }
        }
    }
}
