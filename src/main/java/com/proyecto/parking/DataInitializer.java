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
        crearRolSiNoExiste("SuperAdmin");
        crearRolSiNoExiste("Administrador");
        crearRolSiNoExiste("Cliente");
        crearSuperAdminSiNoExiste();
    }

    private void crearRolSiNoExiste(String nombre) {
        if (rolRepository.findByNombre(nombre) == null) {
            rolRepository.save(new Rol(nombre));
            System.out.println("Rol creado: " + nombre);
        }
    }

    private void crearSuperAdminSiNoExiste() {
        if (usuarioRepository.findByCorreo("superadmin@parking.com") == null) {
            Rol rol = rolRepository.findByNombre("SuperAdmin");
            Usuario superAdmin = new Usuario();
            superAdmin.setNombre("Super Admin");
            superAdmin.setCedula("0000000000");
            superAdmin.setCorreo("superadmin@parking.com");
            superAdmin.setContrasena("superadmin123");
            superAdmin.setRol(rol);
            superAdmin.setHabilitado(true);
            usuarioRepository.save(superAdmin);
            System.out.println("SuperAdmin creado: superadmin@parking.com / superadmin123");
        }
    }
}
