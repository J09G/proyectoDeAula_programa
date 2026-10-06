package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.ReservaRepository;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Panel del administrador sobre un parqueadero: reservas paginadas y configuración en su propia página. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class AdminParqueaderoIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private UsuarioService usuarioService;

    private Usuario ana;
    private Usuario beto;
    private Parqueadero parqueadero;
    private String panel;

    @BeforeEach
    void preparar() {
        reservaRepository.deleteAll();
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
        usuarioService.registrarUsuario("Otro Admin", "2000000002", "otro@correo.com", "clave1234", null, Rol.ADMINISTRADOR);

        Zona zona = new Zona();
        zona.setNombreZona("Centro");
        zona = zonaRepository.save(zona);

        parqueadero = new Parqueadero();
        parqueadero.setNombre("Central");
        parqueadero.setDireccion("Calle 30 # 10-20");
        parqueadero.setHorario("24 horas");
        parqueadero.setTarifaHora(3000.0);
        parqueadero.setEspaciosTotales(50);
        parqueadero.setEspaciosDisponibles(50);
        parqueadero.setHabilitado(true);
        parqueadero.setZona(zona);
        parqueadero.setAdministrador(admin);
        parqueadero = parqueaderoRepository.save(parqueadero);
        panel = "/admin/parqueadero/" + parqueadero.getId();
    }

    private void crearReservas(Usuario cliente, EstadoReserva estado, int cuantas) {
        for (int i = 0; i < cuantas; i++) {
            Reserva reserva = new Reserva(estado, cliente, parqueadero);
            reserva.setEspacioReservado(i + 1);
            reserva.setFechaReserva(LocalDateTime.now().plusHours(2));
            reservaRepository.save(reserva);
        }
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    // ── Reservas paginadas ───────────────────────────────────────────────────

    @Test
    void lasReservasSeMuestranDeDiezEnDiez() throws Exception {
        crearReservas(ana, EstadoReserva.PENDIENTE, 12);
        Cookie admin = entrar("admin@correo.com");

        mockMvc.perform(get(panel).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(model().attribute("reservas", hasSize(10)))
                .andExpect(model().attribute("paginaActual", 0))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalElementos", 12L));

        mockMvc.perform(get(panel).param("pagina", "1").cookie(admin))
                .andExpect(model().attribute("reservas", hasSize(2)));
    }

    @Test
    void losIndicadoresCuentanTodasLasReservasNoSoloLaPaginaVisible() throws Exception {
        crearReservas(ana, EstadoReserva.PENDIENTE, 12);
        crearReservas(beto, EstadoReserva.ACEPTADA, 3);

        mockMvc.perform(get(panel).cookie(entrar("admin@correo.com")))
                .andExpect(model().attribute("totalPendientes", 12L))
                .andExpect(model().attribute("totalAceptadas", 3L));
    }

    @Test
    void laBusquedaPorCedulaSigueFuncionandoConLaPaginacion() throws Exception {
        crearReservas(ana, EstadoReserva.PENDIENTE, 12);
        crearReservas(beto, EstadoReserva.PENDIENTE, 2);

        mockMvc.perform(get(panel).param("cedula", "1000000002").cookie(entrar("admin@correo.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("reservas", hasSize(2)))
                .andExpect(model().attribute("totalElementos", 2L))
                // Al cambiar de página, el filtro viaja en el enlace.
                .andExpect(model().attribute("filtros", "&cedula=1000000002"));
    }

    @Test
    void unaCedulaSinReservasLoDiceClaramente() throws Exception {
        crearReservas(ana, EstadoReserva.PENDIENTE, 1);

        mockMvc.perform(get(panel).param("cedula", "999").cookie(entrar("admin@correo.com")))
                .andExpect(model().attribute("reservas", hasSize(0)))
                .andExpect(model().attribute("error", "No se encontraron reservas para la cédula 999."));
    }

    @Test
    void aceptarUnaReservaVuelveALaMismaPaginaYBusqueda() throws Exception {
        crearReservas(beto, EstadoReserva.PENDIENTE, 1);
        Reserva reserva = reservaRepository.findAll().get(0);

        mockMvc.perform(post(panel + "/reserva/aceptar/" + reserva.getId()).with(csrf())
                        .param("pagina", "1").param("cedula", "1000000002")
                        .cookie(entrar("admin@correo.com")))
                .andExpect(redirectedUrl(panel + "?pagina=1&cedula=1000000002#reservas"));
    }

    // ── Configuración (datos, espacios y estado) en su propia página ─────────

    @Test
    void laConfiguracionTieneSuPropiaPaginaConDatosEspaciosYEstado() throws Exception {
        String html = mockMvc.perform(get(panel + "/editar").cookie(entrar("admin@correo.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/editar_parqueadero"))
                .andExpect(model().attributeExists("parqueadero", "espaciosOcupados", "espaciosPendientes"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("name=\"direccion\""), "Formulario de datos");
        assertTrue(html.contains("cubiculos-grid"), "Mapa de cubículos");
        assertTrue(html.contains("name=\"espaciosTotales\""), "Formulario de capacidad");
        assertTrue(html.contains("name=\"habilitado\""), "Botón de habilitar o deshabilitar");
    }

    @Test
    void cambiarLosEspaciosVuelveAConfiguracion() throws Exception {
        mockMvc.perform(post(panel + "/espacios").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("espaciosTotales", "60")
                        .param("espaciosDisponibles", "60"))
                .andExpect(redirectedUrl(panel + "/editar"))
                .andExpect(flash().attribute("mensaje", "Espacios actualizados correctamente."));

        assertEquals(60, parqueaderoRepository.findById(parqueadero.getId()).orElseThrow().getEspaciosTotales());
    }

    @Test
    void deshabilitarElParqueaderoVuelveAConfiguracion() throws Exception {
        mockMvc.perform(post(panel + "/estado").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("habilitado", "false"))
                .andExpect(redirectedUrl(panel + "/editar"));

        assertFalse(parqueaderoRepository.findById(parqueadero.getId()).orElseThrow().getHabilitado());
    }

    @Test
    void guardarLosDatosVuelveAConfiguracion() throws Exception {
        mockMvc.perform(post(panel + "/editar").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("nombre", "Central Norte")
                        .param("direccion", "Calle 30 # 10-20")
                        .param("horario", "6:00 - 22:00")
                        .param("tarifa", "3500"))
                .andExpect(redirectedUrl(panel + "/editar"))
                .andExpect(flash().attribute("mensaje", "Información actualizada correctamente."));

        assertEquals("Central Norte", parqueaderoRepository.findById(parqueadero.getId()).orElseThrow().getNombre());
    }

    @Test
    void unErrorAlGuardarVuelveAlFormularioDeEdicion() throws Exception {
        mockMvc.perform(post(panel + "/editar").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("nombre", "")
                        .param("direccion", "Calle 30 # 10-20")
                        .param("horario", "24 horas")
                        .param("tarifa", "3500"))
                .andExpect(redirectedUrl(panel + "/editar"))
                .andExpect(flash().attributeExists("error"));

        assertEquals("Central", parqueaderoRepository.findById(parqueadero.getId()).orElseThrow().getNombre());
    }

    @Test
    void otroAdministradorNoPuedeAbrirLaEdicion() throws Exception {
        mockMvc.perform(get(panel + "/editar").cookie(entrar("otro@correo.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elPanelYaNoTraeLaConfiguracion() throws Exception {
        String html = mockMvc.perform(get(panel).cookie(entrar("admin@correo.com")))
                .andReturn().getResponse().getContentAsString();

        assertFalse(html.contains("name=\"direccion\""), "Los datos se editan en /editar");
        assertFalse(html.contains("name=\"espaciosTotales\""), "La capacidad se cambia en /editar");
        assertFalse(html.contains("name=\"habilitado\""), "El estado se cambia en /editar");
        assertTrue(html.contains(panel + "/editar"), "El panel enlaza a la configuración");
    }

    // ── Ubicación en el mapa (US-15, TASK-37) ────────────────────────────────

    private Parqueadero recargado() {
        return parqueaderoRepository.findById(parqueadero.getId()).orElseThrow();
    }

    @Test
    void laConfiguracionTraeElMiniMapa() throws Exception {
        String html = mockMvc.perform(get(panel + "/editar").cookie(entrar("admin@correo.com")))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("id=\"mapa-ubicacion\""), "Contenedor del mini-mapa");
        assertTrue(html.contains("/js/leaflet/leaflet.js"), "Leaflet servido por la propia app");
        assertTrue(html.contains("name=\"latitud\"") && html.contains("name=\"longitud\""));
    }

    @Test
    void leafletSeSirveDesdeLaApp() throws Exception {
        mockMvc.perform(get("/js/leaflet/leaflet.js")).andExpect(status().isOk());
        mockMvc.perform(get("/js/leaflet/leaflet.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/leaflet/images/marker-icon.png")).andExpect(status().isOk());
    }

    @Test
    void ac1_guardarLaUbicacionMarcadaEnElMapa() throws Exception {
        mockMvc.perform(post(panel + "/ubicacion").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("latitud", "10.4236")
                        .param("longitud", "-75.5478"))
                .andExpect(redirectedUrl(panel + "/editar"))
                .andExpect(flash().attribute("mensaje", "Ubicación guardada."));

        assertEquals(10.4236, recargado().getLatitud());
        assertEquals(-75.5478, recargado().getLongitud());
    }

    @Test
    void unaLatitudFueraDeRangoSeRechaza() throws Exception {
        mockMvc.perform(post(panel + "/ubicacion").with(csrf()).cookie(entrar("admin@correo.com"))
                        .param("latitud", "95")
                        .param("longitud", "-75.5478"))
                .andExpect(redirectedUrl(panel + "/editar"))
                .andExpect(flash().attributeExists("error"));

        assertNull(recargado().getLatitud());
    }

    @Test
    void sinMarcarUnPuntoNoSeGuardaNada() throws Exception {
        mockMvc.perform(post(panel + "/ubicacion").with(csrf()).cookie(entrar("admin@correo.com")))
                .andExpect(redirectedUrl(panel + "/editar"))
                .andExpect(flash().attributeExists("error"));

        assertNull(recargado().getLatitud());
    }

    @Test
    void otroAdministradorNoPuedeMoverLaUbicacion() throws Exception {
        mockMvc.perform(post(panel + "/ubicacion").with(csrf()).cookie(entrar("otro@correo.com"))
                        .param("latitud", "10.4")
                        .param("longitud", "-75.5"))
                .andExpect(status().isForbidden());

        assertNull(recargado().getLatitud());
    }
}
