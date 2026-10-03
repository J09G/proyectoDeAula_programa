package com.proyecto.parking.controller;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.TokenRecuperacion;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.TokenRecuperacionRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.UsuarioService;
import com.proyecto.parking.service.impl.RecuperacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-05: recuperar la contraseña por correo, contra el Mongo embebido. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class RecuperacionIT {

    private static final String TOKEN = "token-de-prueba-conocido";

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TokenRecuperacionRepository tokenRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private MongoTemplate mongoTemplate;

    private Usuario ana;

    @BeforeEach
    void preparar() {
        tokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));
        ana = usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "claveVieja1", "ABC123", Rol.CLIENTE);
    }

    /** Un enlace conocido, guardado como lo haría el servicio (solo la huella). */
    private void guardarEnlace(Usuario usuario, Instant expiraEn) {
        tokenRepository.save(new TokenRecuperacion(RecuperacionServiceImpl.huella(TOKEN), usuario.getId(),
                expiraEn.minus(Duration.ofMinutes(30)), expiraEn));
    }

    private String loginCon(String correo, String clave) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", clave))
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    void laRespuestaEsLaMismaExistaONoElCorreo() throws Exception {
        mockMvc.perform(post("/recuperar").with(csrf()).param("correo", "ana@correo.com"))
                .andExpect(redirectedUrl("/recuperar?enviado"));
        mockMvc.perform(post("/recuperar").with(csrf()).param("correo", "nadie@correo.com"))
                .andExpect(redirectedUrl("/recuperar?enviado"));

        assertEquals(1, tokenRepository.count(), "Solo el correo existente genera un enlace");
    }

    @Test
    void enLaBaseSoloQuedaLaHuellaDelToken() throws Exception {
        mockMvc.perform(post("/recuperar").with(csrf()).param("correo", "ana@correo.com"));

        TokenRecuperacion guardado = tokenRepository.findAll().get(0);
        assertTrue(guardado.getHashToken().matches("[0-9a-f]{64}"), "Debe ser un SHA-256 en hexadecimal");
    }

    @Test
    void pedirDosVecesSeguidasNoGeneraOtroEnlace() throws Exception {
        mockMvc.perform(post("/recuperar").with(csrf()).param("correo", "ana@correo.com"));
        String primero = tokenRepository.findAll().get(0).getHashToken();

        mockMvc.perform(post("/recuperar").with(csrf()).param("correo", "ana@correo.com"))
                .andExpect(redirectedUrl("/recuperar?enviado"));

        assertEquals(1, tokenRepository.count());
        assertEquals(primero, tokenRepository.findAll().get(0).getHashToken(),
                "El segundo pedido dentro del minuto se ignora");
    }

    @Test
    void conUnEnlaceVigenteSeCambiaLaContrasenaYElEnlaceNoSirveDosVeces() throws Exception {
        guardarEnlace(ana, Instant.now().plus(Duration.ofMinutes(20)));

        mockMvc.perform(get("/restablecer").param("token", TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(post("/restablecer").with(csrf()).param("token", TOKEN)
                        .param("password", "claveNueva1").param("confirmPassword", "claveNueva1"))
                .andExpect(redirectedUrl("/login?restablecida"));

        assertEquals("/cliente", loginCon("ana@correo.com", "claveNueva1"), "Entra con la nueva");
        assertEquals("/login?error=credenciales", loginCon("ana@correo.com", "claveVieja1"), "La vieja ya no sirve");

        String segundoUso = mockMvc.perform(post("/restablecer").with(csrf()).param("token", TOKEN)
                        .param("password", "otraClave99").param("confirmPassword", "otraClave99"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(segundoUso.contains("El enlace no es válido, ya se usó o venció"));
    }

    @Test
    void unEnlaceVencidoSeRechazaAunqueMongoNoLoHayaBorrado() throws Exception {
        guardarEnlace(ana, Instant.now().minusSeconds(5));

        String html = mockMvc.perform(get("/restablecer").param("token", TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("El enlace no es válido, ya se usó o venció"));
    }

    @Test
    void siLasContrasenasNoCoincidenElEnlaceNoSeGasta() throws Exception {
        guardarEnlace(ana, Instant.now().plus(Duration.ofMinutes(20)));

        mockMvc.perform(post("/restablecer").with(csrf()).param("token", TOKEN)
                        .param("password", "claveNueva1").param("confirmPassword", "otraCosa22"))
                .andExpect(status().isOk());

        assertEquals(1, tokenRepository.count(), "El enlace sigue disponible para reintentar");
        assertEquals("/cliente", loginCon("ana@correo.com", "claveVieja1"));
    }

    @Test
    void unUsuarioDeGoogleSinContrasenaPuedeCrearUna() throws Exception {
        Usuario deGoogle = usuarioService.obtenerOCrearUsuarioGoogle("juan@gmail.com", "Juan");
        guardarEnlace(deGoogle, Instant.now().plus(Duration.ofMinutes(20)));

        mockMvc.perform(post("/restablecer").with(csrf()).param("token", TOKEN)
                        .param("password", "claveNueva1").param("confirmPassword", "claveNueva1"))
                .andExpect(redirectedUrl("/login?restablecida"));

        assertNotEquals("/login?error=credenciales", loginCon("juan@gmail.com", "claveNueva1"));
    }

    @Test
    void laColeccionTieneIndiceTtlParaBorrarLosVencidos() {
        List<IndexInfo> indices = mongoTemplate.indexOps(TokenRecuperacion.class).getIndexInfo();

        assertTrue(indices.stream().anyMatch(i -> i.getExpireAfter().filter(Duration.ZERO::equals).isPresent()),
                "Debe existir el índice TTL sobre expiraEn: " + indices);
    }

    @Test
    void elLoginMuestraElEnlaceDeRecuperacion() throws Exception {
        String html = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("href=\"/recuperar\""));
    }
}
