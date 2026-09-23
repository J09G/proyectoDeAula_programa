package com.proyecto.parking.controller;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion aislada: usa un MongoDB embebido (Flapdoodle), levantado
 * automaticamente por de.flapdoodle.embed.mongo.spring3x. Nunca se conecta a la
 * base de datos real de produccion (MONGODB_URI); no depende de Docker.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @BeforeEach
    void limpiarYSembrarRoles() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol("Cliente"));
        rolRepository.save(new Rol("Administrador"));
        rolRepository.save(new Rol("SuperAdmin"));
    }

    @Test
    void registraUnClienteYDevuelveTokenConEstado201() throws Exception {
        String body = """
                {
                  "nombre": "Ana Torres",
                  "cedula": "1000000001",
                  "correo": "ana@correo.com",
                  "contrasena": "clave1234",
                  "placa": "ABC123"
                }
                """;

        mockMvc.perform(post("/api/auth/registro").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.rol").value("Cliente"));
    }

    @Test
    void rechazaRegistroConCorreoDuplicadoCon409() throws Exception {
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", "Cliente");

        String body = """
                {
                  "nombre": "Otra Persona",
                  "cedula": "1000000002",
                  "correo": "ana@correo.com",
                  "contrasena": "otraclave"
                }
                """;

        mockMvc.perform(post("/api/auth/registro").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rechazaRegistroSinCorreoCon400YDetalleDelCampo() throws Exception {
        String body = """
                {
                  "nombre": "Ana Torres",
                  "cedula": "1000000001",
                  "correo": "",
                  "contrasena": "clave1234"
                }
                """;

        mockMvc.perform(post("/api/auth/registro").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo", notNullValue()));
    }

    @Test
    void loginDevuelveTokenParaUsuarioRecienRegistrado() throws Exception {
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", "Cliente");

        String body = """
                {"correo":"ana@correo.com","password":"clave1234"}
                """;

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    void loginRechazaContrasenaIncorrectaCon401() throws Exception {
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", "Cliente");

        String body = """
                {"correo":"ana@correo.com","password":"clave-mala"}
                """;

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }
}
