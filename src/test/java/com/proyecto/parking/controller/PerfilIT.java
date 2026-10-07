package com.proyecto.parking.controller;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** US-08: cada usuario edita su nombre, su correo y su contraseña. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class PerfilIT {

    private static final String CORREO = "ana@correo.com";
    private static final String CLAVE = "clave1234";

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void preparar() {
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));
        usuarioService.registrarUsuario("Ana Torres", "1000000001", CORREO, CLAVE, "ABC123", Rol.CLIENTE);
        usuarioService.registrarUsuario("Luis Pérez", "1000000002", "luis@correo.com", CLAVE, "XYZ789", Rol.CLIENTE);
    }

    private Usuario ana() {
        return usuarioRepository.findByCorreo(CORREO).orElseThrow();
    }

    /** POST /perfil con la sesión de quien tenga ese correo. */
    private MockHttpServletRequestBuilder guardarPerfilComo(String correoSesion) {
        String token = jwtService.generarToken(correoSesion, Rol.CLIENTE);
        return post("/perfil").cookie(new Cookie(JwtService.NOMBRE_COOKIE, token)).with(csrf());
    }

    @Test
    void ac1_cambiaNombreYCorreoYRenuevaLaCookie() throws Exception {
        String id = ana().getId();

        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana María Torres")
                        .param("correo", "Ana.Nueva@Correo.com")
                        .param("passwordActual", CLAVE))
                .andExpect(redirectedUrl("/perfil"))
                // Con el correo cambiado, la cookie vieja dejaría de reconocer a Ana.
                .andExpect(cookie().exists(JwtService.NOMBRE_COOKIE));

        Usuario actualizada = usuarioRepository.findById(id).orElseThrow();
        assertEquals("Ana María Torres", actualizada.getNombre());
        assertEquals("ana.nueva@correo.com", actualizada.getCorreo(), "El correo se guarda normalizado");
    }

    @Test
    void ac1_cambiarSoloElNombreNoPideContrasena() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana María Torres")
                        .param("correo", CORREO))
                .andExpect(redirectedUrl("/perfil"));

        assertEquals("Ana María Torres", ana().getNombre());
    }

    /*
     * Sin contraseña, quien encuentre la sesión abierta cambiaría el correo por
     * el suyo, pediría "¿Olvidaste tu contraseña?" y se quedaría con la cuenta.
     */
    @Test
    void seguridad_cambiarElCorreoSinLaContrasenaActualSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", "atacante@correo.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("perfil"))
                .andExpect(model().attribute("error", "Escribe tu contraseña actual para cambiar el correo."));

        assertEquals(CORREO, ana().getCorreo(), "El correo no debe cambiar");
    }

    @Test
    void seguridad_cambiarElCorreoConLaContrasenaIncorrectaSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", "atacante@correo.com")
                        .param("passwordActual", "noEsLaMia1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "La contraseña actual no es correcta."));

        assertEquals(CORREO, ana().getCorreo(), "El correo no debe cambiar");
    }

    @Test
    void seguridad_cuentaDeGoogleDebeCrearContrasenaAntesDeCambiarElCorreo() throws Exception {
        usuarioService.obtenerOCrearUsuarioGoogle("juan@gmail.com", "Juan");

        mockMvc.perform(guardarPerfilComo("juan@gmail.com")
                        .param("nombre", "Juan")
                        .param("correo", "otro@correo.com")
                        .param("passwordActual", "loQueSea1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", UsuarioService.SIN_CONTRASENA));

        assertTrue(usuarioRepository.findByCorreo("juan@gmail.com").isPresent(), "El correo no debe cambiar");
    }

    @Test
    void ac1_guardarSuPropioCorreoConMayusculasNoEsUnDuplicado() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", "ANA@correo.com"))
                .andExpect(redirectedUrl("/perfil"));
    }

    @Test
    void ac2_rechazaUnCorreoQueYaEsDeOtroUsuario() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", "luis@correo.com")
                        .param("passwordActual", CLAVE))
                .andExpect(status().isOk())
                .andExpect(view().name("perfil"))
                .andExpect(model().attribute("error", "Ese correo ya pertenece a otro usuario."));

        assertEquals(CORREO, ana().getCorreo(), "El correo no debe cambiar");
    }

    @Test
    void ac3_cambiaLaContrasenaConLaActualCorrecta() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("passwordActual", CLAVE)
                        .param("passwordNueva", "nuevaClave99")
                        .param("passwordConfirmar", "nuevaClave99"))
                .andExpect(redirectedUrl("/perfil"));

        assertTrue(passwordEncoder.matches("nuevaClave99", ana().getContrasena()));
    }

    @Test
    void ac3_rechazaElCambioSiLaContrasenaActualEsIncorrecta() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("passwordActual", "noEsLaMia1")
                        .param("passwordNueva", "nuevaClave99")
                        .param("passwordConfirmar", "nuevaClave99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "La contraseña actual no es correcta."));

        assertTrue(passwordEncoder.matches(CLAVE, ana().getContrasena()), "La contraseña no debe cambiar");
    }

    @Test
    void cuentaDeGoogleSinContrasenaRecibeUnMensajeClaroNoUnError500() throws Exception {
        usuarioService.obtenerOCrearUsuarioGoogle("juan@gmail.com", "Juan");

        mockMvc.perform(guardarPerfilComo("juan@gmail.com")
                        .param("nombre", "Juan")
                        .param("correo", "juan@gmail.com")
                        .param("passwordActual", "loQueSea1")
                        .param("passwordNueva", "nuevaClave99")
                        .param("passwordConfirmar", "nuevaClave99"))
                .andExpect(status().isOk())
                .andExpect(view().name("perfil"))
                .andExpect(model().attribute("error", UsuarioService.SIN_CONTRASENA));

        assertNull(usuarioRepository.findByCorreo("juan@gmail.com").orElseThrow().getContrasena());
    }

    // ── Cédula y placa del cliente ───────────────────────────────────────────

    @Test
    void elFormularioDelClienteTraeSuCedulaYSuPlaca() throws Exception {
        String token = jwtService.generarToken(CORREO, Rol.CLIENTE);
        String html = mockMvc.perform(get("/perfil").cookie(new Cookie(JwtService.NOMBRE_COOKIE, token)))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("value=\"1000000001\""), "Cédula actual");
        assertTrue(html.contains("value=\"ABC123\""), "Placa actual");
    }

    @Test
    void cambiarLaPlacaNoPideContrasenaYSeGuardaEnMayusculas() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1000000001")
                        .param("placa", "kmn45f"))
                .andExpect(redirectedUrl("/perfil"));

        assertEquals("KMN45F", ana().getPlaca());
    }

    @Test
    void cambiarLaCedulaSinContrasenaSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1099999999")
                        .param("placa", "ABC123"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "Escribe tu contraseña actual para cambiar la cédula."));

        assertEquals("1000000001", ana().getCedula());
    }

    @Test
    void cambiarLaCedulaConLaContrasenaCorrecta() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1099999999")
                        .param("placa", "ABC123")
                        .param("passwordActual", CLAVE))
                .andExpect(redirectedUrl("/perfil"));

        assertEquals("1099999999", ana().getCedula());
    }

    @Test
    void cambiarLaCedulaConContrasenaIncorrectaSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1099999999")
                        .param("placa", "ABC123")
                        .param("passwordActual", "noEsLaMia1"))
                .andExpect(model().attribute("error", "La contraseña actual no es correcta."));

        assertEquals("1000000001", ana().getCedula());
    }

    @Test
    void unaCedulaDeOtroUsuarioSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1000000002")
                        .param("placa", "ABC123")
                        .param("passwordActual", CLAVE))
                .andExpect(model().attribute("error", "Esa cédula ya pertenece a otro usuario."));

        assertEquals("1000000001", ana().getCedula());
    }

    @Test
    void unaPlacaDeOtroUsuarioSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1000000001")
                        .param("placa", "xyz789"))
                .andExpect(model().attribute("error", "Esa placa ya pertenece a otro usuario."));

        assertEquals("ABC123", ana().getPlaca());
    }

    @Test
    void unaPlacaConFormatoInvalidoSeRechaza() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1000000001")
                        .param("placa", "123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "placa"));

        assertEquals("ABC123", ana().getPlaca());
    }

    @Test
    void elClienteNoPuedeDejarLaPlacaVacia() throws Exception {
        mockMvc.perform(guardarPerfilComo(CORREO)
                        .param("nombre", "Ana Torres")
                        .param("correo", CORREO)
                        .param("cedula", "1000000001")
                        .param("placa", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", "La placa es obligatoria."));

        assertEquals("ABC123", ana().getPlaca());
    }

    @Test
    void elPerfilDeUnAdministradorNoMuestraNiTocaCedulaYPlaca() throws Exception {
        usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", CLAVE, null, Rol.ADMINISTRADOR);
        String token = jwtService.generarToken("admin@correo.com", Rol.ADMINISTRADOR);
        Cookie sesion = new Cookie(JwtService.NOMBRE_COOKIE, token);

        String html = mockMvc.perform(get("/perfil").cookie(sesion))
                .andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("name=\"placa\""), "El administrador no tiene placa");

        mockMvc.perform(post("/perfil").cookie(sesion).with(csrf())
                        .param("nombre", "Admin Nuevo")
                        .param("correo", "admin@correo.com"))
                .andExpect(redirectedUrl("/perfil"));

        Usuario admin = usuarioRepository.findByCorreo("admin@correo.com").orElseThrow();
        assertEquals("Admin Nuevo", admin.getNombre());
        assertEquals("2000000001", admin.getCedula(), "Su cédula no se toca");
    }
}
