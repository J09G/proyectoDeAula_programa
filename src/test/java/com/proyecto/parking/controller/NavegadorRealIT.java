package com.proyecto.parking.controller;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flujos completos como los hace un navegador real: Tomcat real, cookies
 * guardadas y reenviadas por un CookieManager, y el token CSRF tomado del HTML
 * de la página (no inyectado con csrf() de spring-security-test, que es lo que
 * dejó pasar el 403 al cerrar sesión).
 *
 * <p>Mismas propiedades que ProxyRenderIT para reutilizar su contexto.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5",
                "server.forward-headers-strategy=native"
        })
class NavegadorRealIT {

    private static final Pattern CAMPO_CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @LocalServerPort private int puerto;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;

    private HttpClient navegador;

    @BeforeEach
    void preparar() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));
        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);

        navegador = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        return navegador.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postFormulario(String ruta, String cuerpo) throws Exception {
        return navegador.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String csrfDe(String html) {
        Matcher m = CAMPO_CSRF.matcher(html);
        assertTrue(m.find(), "La página debe traer el token CSRF en sus formularios");
        return URLEncoder.encode(m.group(1), StandardCharsets.UTF_8);
    }

    private void iniciarSesion() throws Exception {
        String csrf = csrfDe(get("/login").body());
        HttpResponse<String> login = postFormulario("/login",
                "email=ana%40correo.com&password=clave1234&_csrf=" + csrf);
        assertTrue(login.headers().firstValue("Location").orElse("").endsWith("/cliente"), "El login debe funcionar");
        assertTrue(login.headers().allValues("Set-Cookie").stream().anyMatch(c -> c.startsWith("XSRF-TOKEN=;")),
                "Al iniciar sesión se descarta el token CSRF que se usó siendo anónimo");
    }

    @Test
    void cerrarSesionDesdeElPanelFuncionaComoEnElNavegador() throws Exception {
        iniciarSesion();

        HttpResponse<String> panel = get("/cliente");
        assertEquals(200, panel.statusCode());

        HttpResponse<String> logout = postFormulario("/logout", "_csrf=" + csrfDe(panel.body()));

        assertEquals(302, logout.statusCode(), "Cerrar sesión no debe dar 403");
        assertTrue(logout.headers().firstValue("Location").orElse("").endsWith("/login?logout"));
        assertEquals(302, get("/cliente").statusCode(), "Tras cerrar sesión, el panel vuelve a pedir login");
    }

    @Test
    void cerrarSesionFuncionaAunqueLaPaginaCargueSusArchivos() throws Exception {
        iniciarSesion();

        HttpResponse<String> panel = get("/cliente");
        assertEquals(200, panel.statusCode());
        // Un navegador real, tras recibir el HTML, pide el CSS, el JS, las fuentes y el icono.
        for (String recurso : new String[] {"/css/style.css", "/css/cliente.css", "/js/app.js", "/favicon.svg"}) {
            HttpResponse<String> r = get(recurso);
            assertEquals(200, r.statusCode(), recurso);
            assertTrue(r.headers().allValues("Set-Cookie").stream().noneMatch(c -> c.startsWith("XSRF-TOKEN=")),
                    "Pedir " + recurso + " no debe tocar la cookie del token CSRF");
        }

        HttpResponse<String> logout = postFormulario("/logout", "_csrf=" + csrfDe(panel.body()));

        assertEquals(302, logout.statusCode(), "Cerrar sesión no debe dar 403 por cargar el CSS o el JS");
        assertTrue(logout.headers().firstValue("Location").orElse("").endsWith("/login?logout"));
    }
}
