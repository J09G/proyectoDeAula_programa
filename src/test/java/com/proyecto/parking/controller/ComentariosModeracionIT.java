package com.proyecto.parking.controller;

import com.proyecto.parking.model.Comentario;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ComentarioRepository;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comentarios: nadie ve quién los escribió salvo el superadministrador, y el
 * parqueadero no puede borrarlos, sólo pedir que se revisen.
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
class ComentariosModeracionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private ComentarioRepository comentarioRepository;
    @Autowired private UsuarioService usuarioService;
    @Autowired private ComentarioService comentarioService;

    private Usuario admin;
    private Parqueadero parqueadero;
    private Parqueadero ajeno;
    private Comentario comentario;

    @BeforeEach
    void preparar() {
        comentarioRepository.deleteAll();
        parqueaderoRepository.deleteAll();
        zonaRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));

        Usuario ana = usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);
        usuarioService.registrarUsuario("Beto Ruiz", "1000000002", "beto@correo.com", "clave1234", "XYZ987", Rol.CLIENTE);
        admin = usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);
        Usuario otroAdmin = usuarioService.registrarUsuario("Otro Admin", "2000000002", "otro@correo.com", "clave1234", null, Rol.ADMINISTRADOR);
        usuarioService.registrarUsuario("Jefa", "3000000001", "super@correo.com", "clave1234", null, Rol.SUPERADMIN);

        Zona zona = new Zona();
        zona.setNombreZona("Centro");
        zona = zonaRepository.save(zona);

        parqueadero = parqueaderoRepository.save(parqueadero("Central", zona, admin));
        ajeno = parqueaderoRepository.save(parqueadero("Ajeno", zona, otroAdmin));

        comentario = comentarioService.crearComentario(ana.getId(), parqueadero.getId(), "Me rayaron el carro", 1);
    }

    private static Parqueadero parqueadero(String nombre, Zona zona, Usuario administrador) {
        Parqueadero p = new Parqueadero();
        p.setNombre(nombre);
        p.setDireccion("Calle 1");
        p.setHorario("6:00 - 22:00");
        p.setTarifaHora(5_000d);
        p.setEspaciosTotales(10);
        p.setEspaciosDisponibles(10);
        p.setHabilitado(true);
        p.setZona(zona);
        p.setAdministrador(administrador);
        return p;
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    private String urlReportar(Parqueadero p) {
        return "/admin/parqueadero/" + p.getId() + "/comentario/" + comentario.getId() + "/reportar";
    }

    @Test
    void otroClienteVeElComentarioPeroNoQuienLoEscribio() throws Exception {
        mockMvc.perform(get("/reserva/" + parqueadero.getId()).cookie(entrar("beto@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Me rayaron el carro")))
                .andExpect(content().string(containsString("Cliente verificado")))
                .andExpect(content().string(not(containsString("Ana Torres"))));
    }

    @Test
    void elParqueaderoTampocoVeQuienLoEscribio() throws Exception {
        mockMvc.perform(get("/admin/parqueadero/" + parqueadero.getId()).cookie(entrar("admin@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Me rayaron el carro")))
                .andExpect(content().string(not(containsString("Ana Torres"))));
    }

    @Test
    void elParqueaderoReportaYElComentarioSigueVisibleHastaQueSeDecida() throws Exception {
        mockMvc.perform(post(urlReportar(parqueadero)).with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("motivo", "No es un cliente nuestro"))
                .andExpect(status().is3xxRedirection());

        Comentario guardado = comentarioRepository.findById(comentario.getId()).orElseThrow();
        assertThat(guardado.isReportado()).isTrue();
        assertThat(guardado.getMotivoReporte()).isEqualTo("No es un cliente nuestro");

        mockMvc.perform(get("/admin/parqueadero/" + parqueadero.getId()).cookie(entrar("admin@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Me rayaron el carro")))
                .andExpect(content().string(containsString("en revisión")));
    }

    @Test
    void reportarExigeUnMotivo() throws Exception {
        mockMvc.perform(post(urlReportar(parqueadero)).with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("motivo", "   "))
                .andExpect(status().is3xxRedirection());

        assertThat(comentarioRepository.findById(comentario.getId()).orElseThrow().isReportado()).isFalse();
    }

    @Test
    void unAdministradorNoPuedeReportarComentariosDeOtroParqueadero() throws Exception {
        mockMvc.perform(post(urlReportar(ajeno)).with(csrf()).cookie(entrar("otro@correo.com"))
                        .param("motivo", "Quiero quitarle la nota a la competencia"))
                .andExpect(status().isForbidden());

        assertThat(comentarioRepository.findById(comentario.getId()).orElseThrow().isReportado()).isFalse();
    }

    @Test
    void elParqueaderoNoPuedeBorrarComentarios() throws Exception {
        mockMvc.perform(post("/superadmin/comentarios/eliminar").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("idComentario", comentario.getId()))
                .andExpect(status().isForbidden());

        assertThat(comentarioRepository.existsById(comentario.getId())).isTrue();
    }

    @Test
    void elSuperadministradorVeElReporteConSuAutorYPuedeEliminarlo() throws Exception {
        comentarioService.reportarComentario(comentario.getId(), admin.getId(), "Insultos");
        Cookie jefa = entrar("super@correo.com");

        mockMvc.perform(get("/superadmin/comentarios").cookie(jefa))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Me rayaron el carro")))
                .andExpect(content().string(containsString("Insultos")))
                .andExpect(content().string(containsString("Ana Torres")));

        mockMvc.perform(post("/superadmin/comentarios/eliminar").with(csrf()).cookie(jefa)
                        .param("idComentario", comentario.getId()))
                .andExpect(status().is3xxRedirection());

        assertThat(comentarioRepository.existsById(comentario.getId())).isFalse();
    }

    @Test
    void siElSuperadministradorDescartaElReporteElComentarioSigue() throws Exception {
        comentarioService.reportarComentario(comentario.getId(), admin.getId(), "No me gusta");

        mockMvc.perform(post("/superadmin/comentarios/descartar").with(csrf()).cookie(entrar("super@correo.com"))
                        .param("idComentario", comentario.getId()))
                .andExpect(status().is3xxRedirection());

        Comentario guardado = comentarioRepository.findById(comentario.getId()).orElseThrow();
        assertThat(guardado.isReportado()).isFalse();
        assertThat(guardado.getMotivoReporte()).isNull();
        assertThat(comentarioService.listarReportados()).isEmpty();
    }
}
