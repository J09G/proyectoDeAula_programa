package com.proyecto.parking.controller;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Un id que no tiene el formato de MongoDB (24 caracteres hexadecimales) es un
 * recurso que no existe: debe dar 404, no un error interno 500.
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
class IdInvalidoIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;

    @BeforeEach
    void preparar() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);
        usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);
    }

    private Cookie entrar() throws Exception {
        return entrar("ana@correo.com");
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    @Test
    void reservarEnUnParqueaderoConIdInvalidoDa404() throws Exception {
        mockMvc.perform(get("/reserva/abc123").cookie(entrar()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    void laFacturaConIdInvalidoDa404() throws Exception {
        mockMvc.perform(get("/cliente/parqueos/no-existe/factura").cookie(entrar()))
                .andExpect(status().isNotFound());
    }

    @Test
    void elPanelDelAdministradorConIdInvalidoDa404() throws Exception {
        Cookie admin = entrar("admin@correo.com");

        mockMvc.perform(get("/admin/parqueadero/abc123").cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/parqueadero/abc123/editar").cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/parqueadero/abc123/registros").cookie(admin))
                .andExpect(status().isNotFound());
    }
}
