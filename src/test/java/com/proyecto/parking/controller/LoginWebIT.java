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
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La app web (Thymeleaf) ya no usa sesion de servidor: la identidad viaja en
 * un JWT dentro de una cookie HttpOnly (ver SecurityConfig, LoginSuccessHandler,
 * JwtCookieAuthenticationFilter). Esta prueba verifica ese flujo completo,
 * sin depender de la base de datos real (Mongo embebido via Flapdoodle).
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
class LoginWebIT {

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
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", "Cliente");
    }

    @Test
    void loginPorFormularioEntregaCookieJwtYRedirigeAlPanelDelCliente() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "ana@correo.com")
                        .param("password", "clave1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cliente"))
                .andReturn();

        Cookie cookieJwt = resultado.getResponse().getCookie(JwtService.NOMBRE_COOKIE);
        assertNotNull(cookieJwt, "El login debe dejar la cookie jwt en la respuesta");
        assertTrue(cookieJwt.isHttpOnly(), "La cookie jwt debe ser HttpOnly");
        assertTrue(cookieJwt.getValue().length() > 10, "La cookie debe traer un token, no un valor vacio");
    }

    @Test
    void sinCookieUnaRutaProtegidaRedirigeAlLogin() throws Exception {
        mockMvc.perform(get("/perfil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void conLaCookieDelLoginSePuedeEntrarAUnaRutaProtegida() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "ana@correo.com")
                        .param("password", "clave1234"))
                .andReturn();
        Cookie cookieJwt = login.getResponse().getCookie(JwtService.NOMBRE_COOKIE);

        mockMvc.perform(get("/perfil").cookie(cookieJwt))
                .andExpect(status().isOk());
    }

    @Test
    void logoutBorraLaCookieJwt() throws Exception {
        MvcResult login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "ana@correo.com")
                        .param("password", "clave1234"))
                .andReturn();
        Cookie cookieJwt = login.getResponse().getCookie(JwtService.NOMBRE_COOKIE);

        MvcResult logout = mockMvc.perform(post("/logout")
                        .with(csrf())
                        .cookie(cookieJwt))
                .andReturn();

        Cookie cookieBorrada = logout.getResponse().getCookie(JwtService.NOMBRE_COOKIE);
        assertNotNull(cookieBorrada, "El logout debe mandar la cookie jwt con Max-Age 0 para borrarla");
        assertEquals(0, cookieBorrada.getMaxAge());
    }

    @Test
    void unTokenManipuladoNoAutentica() throws Exception {
        MockCookie cookieFalsa = new MockCookie(JwtService.NOMBRE_COOKIE, "esto-no-es-un-jwt-valido");

        mockMvc.perform(get("/perfil").cookie(cookieFalsa))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }
}
