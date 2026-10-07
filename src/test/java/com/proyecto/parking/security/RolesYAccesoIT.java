package com.proyecto.parking.security;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
 * US-13 (TASK-34): la matriz de roles por los dos caminos de entrada.
 *
 * <p>Cada rol (cliente, administrador, superadministrador) entra por el
 * formulario y por Google, y con la cookie que recibe se prueba cada panel:
 * solo el suyo le abre. Además, un usuario deshabilitado es rechazado por los
 * dos caminos, tanto al entrar como si ya tenía la cookie de antes.</p>
 *
 * <p>La vuelta de Google se simula igual que en GoogleLoginIT: se le pasa a
 * {@link GoogleLoginSuccessHandler} lo que Spring arma con la respuesta de
 * Google.</p>
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
class RolesYAccesoIT {

    enum Camino { FORMULARIO, GOOGLE }

    private static final String CLAVE = "clave1234";

    /** Un usuario de cada rol, con el panel al que lo lleva el login. */
    private static final Map<String, String> CORREO = Map.of(
            Rol.CLIENTE, "ana@correo.com",
            Rol.ADMINISTRADOR, "admin@parking.com",
            Rol.SUPERADMIN, "super@parking.com");

    private static final Map<String, String> PANEL = Map.of(
            Rol.CLIENTE, "/cliente",
            Rol.ADMINISTRADOR, "/admin",
            Rol.SUPERADMIN, "/superadmin");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ParqueaderoRepository parqueaderoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private GoogleLoginSuccessHandler googleLoginSuccessHandler;

    @BeforeEach
    void sembrarUnUsuarioPorRol() {
        reservaRepository.deleteAll();
        parqueaderoRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));

        usuarioService.registrarUsuario("Ana Torres", "1000000001", CORREO.get(Rol.CLIENTE), CLAVE, "ABC123", Rol.CLIENTE);
        usuarioService.registrarUsuario("Admin Uno", "2000000001", CORREO.get(Rol.ADMINISTRADOR), CLAVE, null, Rol.ADMINISTRADOR);
        usuarioService.registrarUsuario("Super Uno", "3000000001", CORREO.get(Rol.SUPERADMIN), CLAVE, null, Rol.SUPERADMIN);
    }

    // ── Cómo entra cada camino ───────────────────────────────────────────────

    /** Login por el formulario; devuelve la respuesta tal cual (puede ser un rechazo). */
    private MockHttpServletResponse loginFormulario(String correo) throws Exception {
        return mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", correo)
                        .param("password", CLAVE))
                .andReturn().getResponse();
    }

    /** Vuelta de Google con un correo verificado; devuelve la respuesta del handler. */
    private MockHttpServletResponse loginGoogle(String correo) throws Exception {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token-de-prueba")
                .subject("google-" + correo)
                .claim("email", correo)
                .claim("email_verified", true)
                .claim("name", "Usuario de Google")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        OidcUser datosGoogle = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        OAuth2AuthenticationToken autenticacion =
                new OAuth2AuthenticationToken(datosGoogle, datosGoogle.getAuthorities(), "google");

        MockHttpServletResponse response = new MockHttpServletResponse();
        googleLoginSuccessHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, autenticacion);
        return response;
    }

    private MockHttpServletResponse login(Camino camino, String correo) throws Exception {
        return camino == Camino.FORMULARIO ? loginFormulario(correo) : loginGoogle(correo);
    }

    /** Entra como ese rol, comprueba que llegue a su panel y devuelve la cookie jwt. */
    private Cookie entrarComo(String rol, Camino camino) throws Exception {
        MockHttpServletResponse response = login(camino, CORREO.get(rol));
        assertEquals(PANEL.get(rol), response.getRedirectedUrl(),
                rol + " por " + camino + " debe llegar a su panel");
        Cookie cookieJwt = response.getCookie(JwtService.NOMBRE_COOKIE);
        assertNotNull(cookieJwt, rol + " por " + camino + " debe recibir la cookie jwt");
        return cookieJwt;
    }

    private Usuario usuario(String rol) {
        return usuarioRepository.findByCorreo(CORREO.get(rol)).orElseThrow();
    }

    // ── AC1: cada rol solo accede a lo que le corresponde ────────────────────

    /**
     * La matriz: (rol, ruta, código esperado), y cada fila se prueba por los
     * dos caminos. 200 = entra; 403 = página de acceso denegado.
     */
    static Stream<Arguments> matrizDeAcceso() {
        List<Object[]> filas = List.of(
                // Cliente
                new Object[]{Rol.CLIENTE, "/cliente", 200},
                new Object[]{Rol.CLIENTE, "/cliente/mapa", 200},
                new Object[]{Rol.CLIENTE, "/cliente/parqueos", 200},
                new Object[]{Rol.CLIENTE, "/perfil", 200},
                new Object[]{Rol.CLIENTE, "/admin", 403},
                new Object[]{Rol.CLIENTE, "/superadmin", 403},
                new Object[]{Rol.CLIENTE, "/superadmin/usuarios", 403},
                // Administrador
                new Object[]{Rol.ADMINISTRADOR, "/admin", 200},
                new Object[]{Rol.ADMINISTRADOR, "/perfil", 200},
                new Object[]{Rol.ADMINISTRADOR, "/cliente", 403},
                new Object[]{Rol.ADMINISTRADOR, "/cliente/parqueos", 403},
                new Object[]{Rol.ADMINISTRADOR, "/superadmin", 403},
                new Object[]{Rol.ADMINISTRADOR, "/superadmin/usuarios", 403},
                // Superadministrador: tampoco entra al panel de un administrador
                new Object[]{Rol.SUPERADMIN, "/superadmin", 200},
                new Object[]{Rol.SUPERADMIN, "/superadmin/usuarios", 200},
                new Object[]{Rol.SUPERADMIN, "/perfil", 200},
                new Object[]{Rol.SUPERADMIN, "/admin", 403},
                new Object[]{Rol.SUPERADMIN, "/cliente", 403});

        return Stream.of(Camino.values())
                .flatMap(camino -> filas.stream()
                        .map(f -> Arguments.of(f[0], camino, f[1], f[2])));
    }

    @ParameterizedTest(name = "{0} por {1}: GET {2} -> {3}")
    @MethodSource("matrizDeAcceso")
    void cadaRolSoloEntraALoSuyo(String rol, Camino camino, String ruta, int esperado) throws Exception {
        Cookie cookieJwt = entrarComo(rol, camino);

        mockMvc.perform(get(ruta).cookie(cookieJwt))
                .andExpect(status().is(esperado));
    }

    /** Sin cookie, ningún panel abre: todos mandan al login. */
    @ParameterizedTest(name = "sin sesión: GET {0} -> login")
    @MethodSource("panelesProtegidos")
    void sinCookieNingunPanelAbre(String ruta) throws Exception {
        mockMvc.perform(get(ruta))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    static Stream<String> panelesProtegidos() {
        return Stream.of("/cliente", "/cliente/parqueos", "/admin", "/superadmin", "/superadmin/usuarios", "/perfil");
    }

    /**
     * No basta con esconder botones: un formulario de otro rol enviado a mano
     * también se rechaza, y no cambia nada en la base de datos.
     */
    @ParameterizedTest(name = "{0} por {1} no puede deshabilitar usuarios")
    @MethodSource("rolesSinPermisoDeSuperadmin")
    void soloElSuperadminPuedeDeshabilitarUsuarios(String rol, Camino camino) throws Exception {
        Cookie cookieJwt = entrarComo(rol, camino);
        String idCliente = usuario(Rol.CLIENTE).getId();

        mockMvc.perform(conCsrf(post("/superadmin/cambiarEstadoUsuario"), cookieJwt)
                        .param("idUsuario", idCliente)
                        .param("habilitado", "false"))
                .andExpect(status().isForbidden());

        assertTrue(usuario(Rol.CLIENTE).isHabilitado(), "La cuenta no debe haber cambiado");
    }

    static Stream<Arguments> rolesSinPermisoDeSuperadmin() {
        return porCadaCamino(Rol.CLIENTE, Rol.ADMINISTRADOR);
    }

    @ParameterizedTest(name = "{0} por {1} no puede registrar parqueaderos")
    @MethodSource("rolesSinPermisoDeAdministrador")
    void soloElAdministradorPuedeRegistrarParqueaderos(String rol, Camino camino) throws Exception {
        Cookie cookieJwt = entrarComo(rol, camino);

        mockMvc.perform(conCsrf(post("/admin/registrarParqueadero"), cookieJwt)
                        .param("nombre", "Parqueadero Intruso")
                        .param("direccion", "Calle 1")
                        .param("capacidad", "10"))
                .andExpect(status().isForbidden());

        assertEquals(0, parqueaderoRepository.count(), "No debe haberse creado ningún parqueadero");
    }

    static Stream<Arguments> rolesSinPermisoDeAdministrador() {
        return porCadaCamino(Rol.CLIENTE, Rol.SUPERADMIN);
    }

    @ParameterizedTest(name = "{0} por {1} no puede reservar")
    @MethodSource("rolesSinPermisoDeCliente")
    void soloElClientePuedeReservar(String rol, Camino camino) throws Exception {
        Cookie cookieJwt = entrarComo(rol, camino);

        mockMvc.perform(conCsrf(post("/reserva/crear"), cookieJwt)
                        .param("idParqueadero", "000000000000000000000000"))
                .andExpect(status().isForbidden());

        assertEquals(0, reservaRepository.count(), "No debe haberse creado ninguna reserva");
    }

    static Stream<Arguments> rolesSinPermisoDeCliente() {
        return porCadaCamino(Rol.ADMINISTRADOR, Rol.SUPERADMIN);
    }

    private static Stream<Arguments> porCadaCamino(String... roles) {
        return Stream.of(roles)
                .flatMap(rol -> Stream.of(Camino.values()).map(camino -> Arguments.of(rol, camino)));
    }

    private static MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion, Cookie cookieJwt) {
        return peticion.with(csrf()).cookie(cookieJwt);
    }

    // ── AC2: un usuario deshabilitado es rechazado por los dos caminos ───────

    @ParameterizedTest(name = "deshabilitado por {0}: no entra")
    @EnumSource(Camino.class)
    void unDeshabilitadoNoPuedeEntrar(Camino camino) throws Exception {
        usuarioService.cambiarEstadoUsuario(usuario(Rol.CLIENTE).getId(), false);

        MockHttpServletResponse response = login(camino, CORREO.get(Rol.CLIENTE));

        assertEquals("/login?error=deshabilitado", response.getRedirectedUrl());
        assertNull(response.getCookie(JwtService.NOMBRE_COOKIE), "No debe recibir la cookie jwt");
    }

    /**
     * El caso peligroso: el superadmin deshabilita a alguien que ya estaba
     * adentro. Su cookie todavía no vence, pero deja de servir en la siguiente
     * petición.
     */
    @ParameterizedTest(name = "deshabilitado por {0} con la sesión abierta: pierde el acceso")
    @EnumSource(Camino.class)
    void laCookieDeAntesDejaDeServirAlDeshabilitarlo(Camino camino) throws Exception {
        Cookie cookieJwt = entrarComo(Rol.CLIENTE, camino);
        mockMvc.perform(get("/cliente").cookie(cookieJwt)).andExpect(status().isOk());

        usuarioService.cambiarEstadoUsuario(usuario(Rol.CLIENTE).getId(), false);

        mockMvc.perform(get("/cliente").cookie(cookieJwt))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @ParameterizedTest(name = "rehabilitado por {0}: vuelve a entrar")
    @EnumSource(Camino.class)
    void alRehabilitarloPuedeVolverAEntrar(Camino camino) throws Exception {
        String id = usuario(Rol.CLIENTE).getId();
        usuarioService.cambiarEstadoUsuario(id, false);
        usuarioService.cambiarEstadoUsuario(id, true);

        Cookie cookieJwt = entrarComo(Rol.CLIENTE, camino);

        mockMvc.perform(get("/cliente").cookie(cookieJwt)).andExpect(status().isOk());
    }

    /** El rol sale de la base de datos en cada petición, no de la cookie. */
    @ParameterizedTest(name = "rol cambiado por {0}: la cookie de antes no conserva el rol viejo")
    @EnumSource(Camino.class)
    void unAdministradorDegradadoPierdeElPanelAunqueTengaLaCookie(Camino camino) throws Exception {
        Cookie cookieJwt = entrarComo(Rol.ADMINISTRADOR, camino);
        mockMvc.perform(get("/admin").cookie(cookieJwt)).andExpect(status().isOk());

        Usuario admin = usuario(Rol.ADMINISTRADOR);
        admin.setRol(rolRepository.findByNombre(Rol.CLIENTE).orElseThrow());
        usuarioRepository.save(admin);

        mockMvc.perform(get("/admin").cookie(cookieJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaContrasenaEquivocadaNoEntregaCookie() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", CORREO.get(Rol.ADMINISTRADOR))
                        .param("password", "otra-clave"))
                .andExpect(redirectedUrl("/login?error=credenciales"))
                .andReturn();

        assertNull(resultado.getResponse().getCookie(JwtService.NOMBRE_COOKIE));
    }
}
