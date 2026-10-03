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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login con Google sin el Google real. Se prueban las dos mitades del viaje:
 * la ida (el botón manda a Google con state y client_id) por MockMvc, y la
 * vuelta pasándole a {@link GoogleLoginSuccessHandler} el mismo tipo de objeto
 * que Spring arma con la respuesta de Google. El canje del código por tokens,
 * en medio, es de Spring Security y no se reprueba aquí.
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
class GoogleLoginIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private GoogleLoginSuccessHandler googleLoginSuccessHandler;

    @BeforeEach
    void limpiarYSembrarRoles() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));
    }

    /** Simula la vuelta de Google: lo que Spring entrega al handler tras canjear el código. */
    private MockHttpServletResponse volverDeGoogle(String correo, String nombre, boolean verificado,
                                                   MockHttpServletRequest request) throws Exception {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-de-prueba")
                .subject("google-" + correo)
                .claim("email", correo)
                .claim("email_verified", verificado)
                .claim("name", nombre)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        OidcUser datosGoogle = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        OAuth2AuthenticationToken autenticacion =
                new OAuth2AuthenticationToken(datosGoogle, datosGoogle.getAuthorities(), "google");

        MockHttpServletResponse response = new MockHttpServletResponse();
        googleLoginSuccessHandler.onAuthenticationSuccess(request, response, autenticacion);
        return response;
    }

    private MockHttpServletResponse volverDeGoogle(String correo, String nombre, boolean verificado) throws Exception {
        return volverDeGoogle(correo, nombre, verificado, new MockHttpServletRequest());
    }

    @Test
    void elBotonDeGoogleRedirigeAGoogleConStateYClientId() throws Exception {
        String destino = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        assertNotNull(destino);
        assertTrue(destino.startsWith("https://accounts.google.com/"), "Debe ir a Google: " + destino);
        assertTrue(destino.contains("state="), "Debe llevar el state que protege el viaje");
        assertTrue(destino.contains("client_id="), "Debe llevar el client_id de la app");
        assertTrue(destino.contains("redirect_uri=http://localhost/login/oauth2/code/google"),
                "Google debe devolver al usuario al callback de la app: " + destino);
    }

    @Test
    void unUsuarioNuevoSeCreaComoClienteSinContrasenaNiCedulaYRecibeLaCookie() throws Exception {
        MockHttpServletResponse response = volverDeGoogle("Juan.Perez@Gmail.com", "Juan Pérez", true);

        assertEquals("/cliente", response.getRedirectedUrl());
        Cookie cookieJwt = response.getCookie(JwtService.NOMBRE_COOKIE);
        assertNotNull(cookieJwt, "Debe entregar la misma cookie jwt que el login por formulario");
        assertTrue(cookieJwt.isHttpOnly());

        Usuario creado = usuarioRepository.findByCorreo("juan.perez@gmail.com").orElseThrow();
        assertEquals("Juan Pérez", creado.getNombre());
        assertEquals(Rol.CLIENTE, creado.getRol().getNombre());
        assertNull(creado.getContrasena());
        assertNull(creado.getCedula());
    }

    @Test
    void variosUsuariosDeGoogleSinCedulaPuedenCoexistir() throws Exception {
        // Con el índice de cédula no disperso, el segundo fallaba por clave duplicada (null).
        volverDeGoogle("uno@gmail.com", "Uno", true);
        volverDeGoogle("dos@gmail.com", "Dos", true);

        assertEquals(2, usuarioRepository.count());
    }

    @Test
    void unCorreoYaRegistradoConContrasenaSeVinculaSinDuplicarse() throws Exception {
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);

        MockHttpServletResponse response = volverDeGoogle("ana@correo.com", "Ana T.", true);

        assertEquals("/cliente", response.getRedirectedUrl());
        assertEquals(1, usuarioRepository.count());
        Usuario ana = usuarioRepository.findByCorreo("ana@correo.com").orElseThrow();
        assertEquals("Ana Torres", ana.getNombre(), "Vincular no debe pisar los datos de la cuenta");
        assertNotNull(ana.getContrasena(), "Debe poder seguir entrando también con su contraseña");
    }

    @Test
    void elRolSaleDeLaBaseDeDatosYNoDeGoogle() throws Exception {
        usuarioService.registrarUsuario("Admin Uno", "2000000001", "admin@parking.com", "clave1234", null, Rol.ADMINISTRADOR);

        MockHttpServletResponse response = volverDeGoogle("admin@parking.com", "Admin", true);

        assertEquals("/admin", response.getRedirectedUrl());
    }

    @Test
    void unaCuentaDeshabilitadaEsRechazada() throws Exception {
        Usuario ana = usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", null, Rol.CLIENTE);
        usuarioService.cambiarEstadoUsuario(ana.getId(), false);

        MockHttpServletResponse response = volverDeGoogle("ana@correo.com", "Ana", true);

        assertEquals("/login?error=deshabilitado", response.getRedirectedUrl());
        assertNull(response.getCookie(JwtService.NOMBRE_COOKIE));
    }

    @Test
    void unCorreoNoVerificadoPorGoogleEsRechazadoYNoCreaNada() throws Exception {
        MockHttpServletResponse response = volverDeGoogle("falso@gmail.com", "Falso", false);

        assertEquals("/login?error=google", response.getRedirectedUrl());
        assertNull(response.getCookie(JwtService.NOMBRE_COOKIE));
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void laSesionTemporalDelViajeAGoogleSeDestruye() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession sesion = new MockHttpSession();
        request.setSession(sesion);

        volverDeGoogle("juan@gmail.com", "Juan", true, request);

        assertTrue(sesion.isInvalid(), "La sesión del state no debe sobrevivir al login");
    }

    @Test
    void laCookieDeGoogleSirveParaEntrarAUnaRutaProtegida() throws Exception {
        Cookie cookieJwt = volverDeGoogle("juan@gmail.com", "Juan", true).getCookie(JwtService.NOMBRE_COOKIE);

        mockMvc.perform(get("/perfil").cookie(cookieJwt))
                .andExpect(status().isOk());
    }

    @Test
    void unUsuarioDeGoogleQueIntentaEntrarConContrasenaVeErrorDeCredencialesNoUn500() throws Exception {
        volverDeGoogle("juan@gmail.com", "Juan", true);

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "juan@gmail.com")
                        .param("password", "cualquier-cosa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=credenciales"));
    }

    @Test
    void laPaginaDeLoginMuestraElBotonDeGoogle() throws Exception {
        String html = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("/oauth2/authorization/google"));
    }
}
