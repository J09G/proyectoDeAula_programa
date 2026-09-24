package com.proyecto.parking.config;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Siembra los roles del sistema y el superadministrador inicial.
 *
 * <p>La contraseña del superadmin se toma de {@code SUPERADMIN_PASSWORD}. Si esa
 * variable no está definida se genera una aleatoria y se imprime UNA sola vez en
 * el log de arranque: así nunca hay una credencial por defecto conocida en el
 * código fuente.</p>
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    private final String superadminEmail;
    private final String superadminPassword;
    private final String superadminCedula;

    public DataInitializer(RolRepository rolRepository,
                           UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${SUPERADMIN_EMAIL:superadmin@parking.com}") String superadminEmail,
                           @Value("${SUPERADMIN_PASSWORD:}") String superadminPassword,
                           @Value("${SUPERADMIN_CEDULA:0000000000}") String superadminCedula) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.superadminEmail = superadminEmail;
        this.superadminPassword = superadminPassword;
        this.superadminCedula = superadminCedula;
    }

    @Override
    public void run(String... args) {
        crearRolSiFalta(Rol.SUPERADMIN);
        crearRolSiFalta(Rol.ADMINISTRADOR);
        crearRolSiFalta(Rol.CLIENTE);
        crearSuperadminSiFalta();
    }

    private void crearRolSiFalta(String nombre) {
        if (!rolRepository.existsByNombre(nombre)) {
            rolRepository.save(new Rol(nombre));
            log.info("Rol '{}' creado.", nombre);
        }
    }

    private void crearSuperadminSiFalta() {
        Rol rol = rolRepository.findByNombre(Rol.SUPERADMIN)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear el rol SuperAdmin."));

        // Basta con que exista UN superadministrador, sea cual sea su correo.
        // Comprobar sólo el correo hacía que, al arrancar con un SUPERADMIN_EMAIL
        // distinto, intentara crear otro con la cédula por defecto y chocara con
        // el índice único de cedula.
        if (usuarioRepository.existsByRol(rol)) {
            return;
        }
        if (usuarioRepository.existsByCorreo(superadminEmail)
                || usuarioRepository.existsByCedula(superadminCedula)) {
            log.warn("No se creó el superadministrador: ya existe un usuario con ese correo o cédula.");
            return;
        }

        boolean generada = superadminPassword == null || superadminPassword.isBlank();
        String password = generada ? generarPassword() : superadminPassword;

        Usuario sa = new Usuario();
        sa.setNombre("Super Admin");
        sa.setCorreo(superadminEmail);
        sa.setContrasena(passwordEncoder.encode(password));
        sa.setCedula(superadminCedula);
        sa.setRol(rol);
        sa.setHabilitado(true);
        usuarioRepository.save(sa);

        if (generada) {
            log.warn("""
                    
                    ===========================================================
                     SUPERADMINISTRADOR CREADO
                       correo     : {}
                       contraseña : {}
                     Guárdala ahora: no se volverá a mostrar.
                     Defínela tú con la variable SUPERADMIN_PASSWORD.
                    ===========================================================
                    """, superadminEmail, password);
        } else {
            log.info("Superadministrador creado con el correo {}.", superadminEmail);
        }
    }

    private String generarPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
