package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** US-12: el cliente ve el historial de sus parqueos y descarga sus facturas. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class MisParqueosIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private RegistroParqueoRepository registroRepository;
    @Autowired private UsuarioService usuarioService;

    private Usuario ana;
    private Usuario beto;
    private Parqueadero parqueadero;

    @BeforeEach
    void preparar() {
        registroRepository.deleteAll();
        parqueaderoRepository.deleteAll();
        zonaRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));

        ana = usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);
        beto = usuarioService.registrarUsuario("Beto Ruiz", "1000000002", "beto@correo.com", "clave1234", "XYZ987", Rol.CLIENTE);
        Usuario admin = usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);

        Zona zona = new Zona();
        zona.setNombreZona("Centro");
        zona = zonaRepository.save(zona);

        parqueadero = new Parqueadero();
        parqueadero.setNombre("Central");
        parqueadero.setDireccion("Calle 30 # 10-20");
        parqueadero.setTarifaHora(3000.0);
        parqueadero.setEspaciosTotales(20);
        parqueadero.setEspaciosDisponibles(20);
        parqueadero.setHabilitado(true);
        parqueadero.setZona(zona);
        parqueadero.setAdministrador(admin);
        parqueadero = parqueaderoRepository.save(parqueadero);
    }

    private RegistroParqueo finalizado(Usuario cliente) {
        RegistroParqueo r = new RegistroParqueo();
        r.setPlaca(cliente.getPlaca());
        r.setUsuario(cliente);
        r.setParqueadero(parqueadero);
        r.setEspacioReservado(3);
        r.setHoraEntrada(LocalDateTime.now().minusMinutes(90));
        r.setHoraSalida(LocalDateTime.now());
        r.setTiempoMinutos(90L);
        r.setValorPagado(4500.0);
        r.setTarifaHoraAplicada(3000.0);
        r.setEstado(EstadoRegistro.FINALIZADO);
        return registroRepository.save(r);
    }

    private RegistroParqueo activo(Usuario cliente) {
        RegistroParqueo r = new RegistroParqueo();
        r.setPlaca(cliente.getPlaca());
        r.setUsuario(cliente);
        r.setParqueadero(parqueadero);
        r.setEspacioReservado(4);
        r.setHoraEntrada(LocalDateTime.now().minusMinutes(20));
        r.setTarifaHoraAplicada(3000.0);
        r.setEstado(EstadoRegistro.ACTIVO);
        return registroRepository.save(r);
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    // ── AC1: historial propio, con paginación ────────────────────────────────

    @Test
    void ac1_elClienteVeSusParqueosDeDiezEnDiez() throws Exception {
        for (int i = 0; i < 12; i++) {
            finalizado(ana);
        }
        Cookie sesion = entrar("ana@correo.com");

        mockMvc.perform(get("/cliente/parqueos").cookie(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("cliente/parqueos"))
                .andExpect(model().attribute("registros", hasSize(10)))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalElementos", 12L));

        mockMvc.perform(get("/cliente/parqueos").param("pagina", "1").cookie(sesion))
                .andExpect(model().attribute("registros", hasSize(2)));
    }

    @Test
    void ac1_muestraEntradaSalidaTotalYEstado() throws Exception {
        finalizado(ana);
        activo(ana);

        String html = mockMvc.perform(get("/cliente/parqueos").cookie(entrar("ana@correo.com")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("Central"), "Nombre del parqueadero");
        assertTrue(html.contains("$4.500"), "Total pagado");
        assertTrue(html.contains("1h 30min"), "Tiempo");
        assertTrue(html.contains("estado-activo") && html.contains("estado-finalizado"), "Estados");
    }

    @Test
    void ac3_cadaClienteSoloVeLoSuyo() throws Exception {
        finalizado(ana);
        finalizado(beto);
        finalizado(beto);

        mockMvc.perform(get("/cliente/parqueos").cookie(entrar("ana@correo.com")))
                .andExpect(model().attribute("registros", hasSize(1)))
                .andExpect(model().attribute("totalElementos", 1L));
    }

    @Test
    void sinParqueosSeMuestraUnMensaje() throws Exception {
        mockMvc.perform(get("/cliente/parqueos").cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("registros", List.of()));
    }

    // ── AC2: factura en PDF de los parqueos finalizados ──────────────────────

    @Test
    void ac2_descargaLaFacturaDeUnParqueoFinalizado() throws Exception {
        RegistroParqueo registro = finalizado(ana);

        byte[] pdf = mockMvc.perform(get("/cliente/parqueos/" + registro.getId() + "/factura")
                        .cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII), "Es un PDF de verdad");
    }

    @Test
    void ac2_soloLosFinalizadosTienenEnlaceDeFactura() throws Exception {
        RegistroParqueo terminado = finalizado(ana);
        RegistroParqueo enCurso = activo(ana);

        String html = mockMvc.perform(get("/cliente/parqueos").cookie(entrar("ana@correo.com")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("/cliente/parqueos/" + terminado.getId() + "/factura"));
        assertFalse(html.contains("/cliente/parqueos/" + enCurso.getId() + "/factura"));
    }

    @Test
    void unParqueoQueSigueActivoNoTieneFactura() throws Exception {
        RegistroParqueo enCurso = activo(ana);

        mockMvc.perform(get("/cliente/parqueos/" + enCurso.getId() + "/factura").cookie(entrar("ana@correo.com")))
                .andExpect(status().isNotFound());
    }

    // ── AC3: lo ajeno responde 404 ───────────────────────────────────────────

    @Test
    void ac3_laFacturaDeOtroClienteResponde404() throws Exception {
        RegistroParqueo deBeto = finalizado(beto);

        mockMvc.perform(get("/cliente/parqueos/" + deBeto.getId() + "/factura").cookie(entrar("ana@correo.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void unIdQueNoExisteResponde404() throws Exception {
        mockMvc.perform(get("/cliente/parqueos/no-existe/factura").cookie(entrar("ana@correo.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void misParqueosEsSoloParaClientes() throws Exception {
        mockMvc.perform(get("/cliente/parqueos").cookie(entrar("admin@correo.com")))
                .andExpect(status().isForbidden());
    }
}
