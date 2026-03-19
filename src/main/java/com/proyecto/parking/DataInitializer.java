package com.proyecto.parking;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Override
    public void run(String... args) {
        // Solo inicializa si no hay roles (base de datos vacía)
        if (rolRepository.count() > 0) return;

        rolRepository.save(new Rol("SuperAdmin"));
        rolRepository.save(new Rol("Administrador"));
        rolRepository.save(new Rol("Cliente"));

        Rol rolSuperAdmin = rolRepository.findByNombre("SuperAdmin");
        Usuario superAdmin = new Usuario();
        superAdmin.setNombre("Super Admin");
        superAdmin.setCedula("0000000000");
        superAdmin.setCorreo("superadmin@parking.com");
        superAdmin.setContrasena("superadmin123");
        superAdmin.setRol(rolSuperAdmin);
        superAdmin.setHabilitado(true);
        usuarioRepository.save(superAdmin);

        System.out.println("Datos iniciales creados.");
    }
}
