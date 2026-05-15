package com.proyecto.parking.config;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (rolRepository.count() == 0) {
            rolRepository.save(new Rol("SuperAdmin"));
            rolRepository.save(new Rol("Administrador"));
            rolRepository.save(new Rol("Cliente"));
            System.out.println("==> Roles creados.");
        }

        if (usuarioRepository.findByCorreo("superadmin@parking.com") == null) {
            Rol rolSuperAdmin = rolRepository.findByNombre("SuperAdmin");
            Usuario sa = new Usuario();
            sa.setNombre("Super Admin");
            sa.setCorreo("superadmin@parking.com");
            sa.setContrasena(passwordEncoder.encode("super123"));
            sa.setCedula("0000000000");
            sa.setRol(rolSuperAdmin);
            sa.setHabilitado(true);
            usuarioRepository.save(sa);
            System.out.println("==> SuperAdmin creado.");
        }
    }
}
