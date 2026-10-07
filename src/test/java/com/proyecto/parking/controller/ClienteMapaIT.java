package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** US-15 (TASK-39): el cliente ve los parqueaderos en un mapa y reserva desde ahí. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class ClienteMapaIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private UsuarioService usuarioService;

    private Usuario admin;
    private Zona zona;

    @BeforeEach
    void preparar() {
        parqueaderoRepository.deleteAll();
        zonaRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));

        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);
        admin = usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);

        zona = new Zona();
        zona.setNombreZona("Centro");
        zona = zonaRepository.save(zona);
    }

    private Parqueadero crear(String nombre, boolean habilitado, Double latitud, Double longitud) {
        Parqueadero p = new Parqueadero();
        p.setNombre(nombre);
        p.setDireccion("Calle 30 # 10-20");
        p.setHorario("24 horas");
        p.setTarifaHora(3000.0);
        p.setEspaciosTotales(20);
        p.setEspaciosDisponibles(7);
        p.setHabilitado(habilitado);
        p.setLatitud(latitud);
        p.setLongitud(longitud);
        p.setZona(zona);
        p.setAdministrador(admin);
        return parqueaderoRepository.save(p);
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    private String mapaComo(String correo) throws Exception {
        return mockMvc.perform(get("/cliente/mapa").cookie(entrar(correo)))
                .andExpect(status().isOk())
                .andExpect(view().name("cliente/mapa"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void ac2_elClienteVeLosParqueaderosHabilitadosConUbicacionYPuedeReservar() throws Exception {
        Parqueadero central = crear("Central", true, 10.4236, -75.5478);

        String html = mapaComo("ana@correo.com");

        assertTrue(html.contains("id=\"mapa-clientes\""), "Contenedor del mapa");
        assertTrue(html.contains("/js/leaflet/leaflet.js"), "Leaflet servido por la propia app");
        assertTrue(html.contains("Central"));
        assertTrue(html.contains("data-lat=\"10.4236\"") && html.contains("data-lng=\"-75.5478\""));
        assertTrue(html.contains("/reserva/" + central.getId()), "Desde el mapa se llega a reservar");
    }

    @Test
    void ac3_losParqueaderosSinUbicacionNoSalenEnElMapa() throws Exception {
        crear("Con ubicación", true, 10.4236, -75.5478);
        crear("Sin ubicación", true, null, null);

        String html = mapaComo("ana@correo.com");

        assertTrue(html.contains("Con ubicación"));
        assertFalse(html.contains("Sin ubicación"), "Sin coordenadas no hay dónde dibujarlo");
    }

    @Test
    void losParqueaderosDeshabilitadosNoSalenEnElMapa() throws Exception {
        crear("Abierto", true, 10.42, -75.54);
        crear("Cerrado", false, 10.43, -75.55);

        String html = mapaComo("ana@correo.com");

        assertTrue(html.contains("Abierto"));
        assertFalse(html.contains("Cerrado"), "Igual que en la búsqueda por zona");
    }

    @Test
    void unNombreConHtmlSeMuestraComoTextoNoSeEjecuta() throws Exception {
        crear("<script>alert(1)</script>", true, 10.42, -75.54);

        String html = mapaComo("ana@correo.com");

        assertFalse(html.contains("<script>alert(1)</script>"), "Thymeleaf debe escapar el nombre");
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void sinParqueaderosUbicadosElMapaLoDice() throws Exception {
        crear("Sin ubicación", true, null, null);

        mockMvc.perform(get("/cliente/mapa").cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("parqueaderos", java.util.List.of()));
    }

    @Test
    void elMapaEsSoloParaClientes() throws Exception {
        mockMvc.perform(get("/cliente/mapa").cookie(entrar("admin@correo.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinSesionPideIniciarSesion() throws Exception {
        mockMvc.perform(get("/cliente/mapa"))
                .andExpect(status().is3xxRedirection());
    }
}
