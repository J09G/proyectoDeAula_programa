package com.proyecto.parking.controller;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.security.GoogleLoginSuccessHandler;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-04: quien entra con Google debe completar cédula y placa antes de reservar. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class CompletarPerfilIT {

    private static final String CORREO_GOOGLE = "juan@gmail.com";
    /** Id con formato de ObjectId (24 hex); el parqueadero no necesita existir. */
    private static final String RESERVA = "/reserva/64b7f0c2a1b2c3d4e5f60718";

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private GoogleLoginSuccessHandler googleLoginSuccessHandler;

    @BeforeEach
    void limpiarYSembrarRoles() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));
    }

    /** Juan entra con Google (simulado) y queda creado sin cédula ni placa. */
    private Cookie entrarConGoogle() throws Exception {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-de-prueba")
                .subject("google-juan").claim("email", CORREO_GOOGLE).claim("email_verified", true)
                .claim("name", "Juan").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();
        var datosGoogle = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        googleLoginSuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                new OAuth2AuthenticationToken(datosGoogle, datosGoogle.getAuthorities(), "google"));
        return response.getCookie(JwtService.NOMBRE_COOKIE);
    }

    private Cookie entrarConContrasena(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    private Usuario juan() {
        return usuarioRepository.findByCorreo(CORREO_GOOGLE).orElseThrow();
    }

    @Test
    void alIrAReservarSinCedulaNiPlacaSeLeMandaACompletarlasRecordandoADondeIba() throws Exception {
        Cookie cookie = entrarConGoogle();

        // El parámetro va dentro de la URL, como lo manda el navegador; con
        // .param() MockMvc no lo pone en la query string.
        String destino = mockMvc.perform(get(RESERVA + "?llegada=2026-10-03T10:00").cookie(cookie))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertTrue(destino.startsWith("/cliente/completar-perfil?continuar="), destino);
        String continuar = UriComponentsBuilder.fromUriString(destino).build().getQueryParams().getFirst("continuar");
        assertEquals(RESERVA + "?llegada=2026-10-03T10:00", URLDecoder.decode(continuar, StandardCharsets.UTF_8));
    }

    @Test
    void sinCompletarElPerfilPuedeVerElPanelYLasZonas() throws Exception {
        mockMvc.perform(get("/cliente").cookie(entrarConGoogle()))
                .andExpect(status().isOk());
    }

    @Test
    void conDatosValidosSeGuardanYVuelveALaReservaQueQueriaVer() throws Exception {
        Cookie cookie = entrarConGoogle();

        mockMvc.perform(post("/cliente/completar-perfil").with(csrf()).cookie(cookie)
                        .param("cedula", "1234567890").param("placa", "abc123")
                        .param("continuar", RESERVA))
                .andExpect(redirectedUrl(RESERVA));

        assertEquals("1234567890", juan().getCedula());
        assertEquals("ABC123", juan().getPlaca(), "La placa se guarda en mayúsculas, igual que en el registro");

        // Con la misma cookie, la siguiente petición ya ve el perfil completo
        // (el filtro relee al usuario de la BD en cada petición).
        String siguiente = mockMvc.perform(get(RESERVA).cookie(cookie))
                .andReturn().getResponse().getRedirectedUrl();
        assertFalse(siguiente != null && siguiente.contains("completar-perfil"));
    }

    @Test
    void unaCedulaDeOtroUsuarioSeRechazaConMensaje() throws Exception {
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "XYZ987", Rol.CLIENTE);
        Cookie cookie = entrarConGoogle();

        String html = mockMvc.perform(post("/cliente/completar-perfil").with(csrf()).cookie(cookie)
                        .param("cedula", "1000000001").param("placa", "ABC123"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("La cédula ya está registrada."));
        assertNull(juan().getCedula());
    }

    @Test
    void unFormatoInvalidoNoSeGuarda() throws Exception {
        Cookie cookie = entrarConGoogle();

        mockMvc.perform(post("/cliente/completar-perfil").with(csrf()).cookie(cookie)
                        .param("cedula", "12").param("placa", "no-es-placa"))
                .andExpect(status().isOk());

        assertNull(juan().getCedula());
        assertNull(juan().getPlaca());
    }

    @Test
    void unContinuarHaciaOtroSitioSeIgnora() throws Exception {
        Cookie cookie = entrarConGoogle();

        mockMvc.perform(post("/cliente/completar-perfil").with(csrf()).cookie(cookie)
                        .param("cedula", "1234567890").param("placa", "ABC123")
                        .param("continuar", "https://sitio-falso.com/login"))
                .andExpect(redirectedUrl("/cliente"));
    }

    @Test
    void quienYaTieneCedulaYPlacaNoVeEstePaso() throws Exception {
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "XYZ987", Rol.CLIENTE);
        Cookie cookie = entrarConContrasena("ana@correo.com");

        mockMvc.perform(get("/cliente/completar-perfil").cookie(cookie))
                .andExpect(redirectedUrl("/cliente"));

        String alReservar = mockMvc.perform(get(RESERVA).cookie(cookie))
                .andReturn().getResponse().getRedirectedUrl();
        assertFalse(alReservar != null && alReservar.contains("completar-perfil"));
    }

    @Test
    void unaCedulaQueYaTieneNoSeCambiaDesdeAqui() throws Exception {
        // Registrado por la API sin placa: tiene cédula pero le falta la placa.
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", null, Rol.CLIENTE);
        Cookie cookie = entrarConContrasena("ana@correo.com");

        mockMvc.perform(post("/cliente/completar-perfil").with(csrf()).cookie(cookie)
                        .param("cedula", "9999999999").param("placa", "ABC123"))
                .andExpect(status().is3xxRedirection());

        Usuario ana = usuarioRepository.findByCorreo("ana@correo.com").orElseThrow();
        assertEquals("1000000001", ana.getCedula(), "La cédula existente no se toca");
        assertEquals("ABC123", ana.getPlaca(), "La placa que faltaba sí se guarda");
    }
}
