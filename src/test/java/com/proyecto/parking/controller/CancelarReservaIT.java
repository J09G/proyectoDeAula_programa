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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-06: el cliente cancela su reserva, contra el Mongo embebido. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class CancelarReservaIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private UsuarioService usuarioService;

    private Usuario ana;
    private Parqueadero parqueadero;

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
        usuarioService.registrarUsuario("Beto Ruiz", "1000000002", "beto@correo.com", "clave1234", "XYZ987", Rol.CLIENTE);
        Usuario admin = usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);

        Zona zona = new Zona();
        zona.setNombreZona("Centro");
        zona = zonaRepository.save(zona);

        parqueadero = new Parqueadero();
        parqueadero.setNombre("Central");
        parqueadero.setEspaciosTotales(10);
        parqueadero.setEspaciosDisponibles(4);
        parqueadero.setHabilitado(true);
        parqueadero.setZona(zona);
        parqueadero.setAdministrador(admin);
        parqueadero = parqueaderoRepository.save(parqueadero);
    }

    private Reserva reservaDe(Usuario cliente, EstadoReserva estado) {
        Reserva reserva = new Reserva(estado, cliente, parqueadero);
        reserva.setEspacioReservado(3);
        reserva.setFechaReserva(LocalDateTime.now().plusHours(2));
        return reservaRepository.save(reserva);
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    private int cuposLibres() {
        return parqueaderoRepository.findById(parqueadero.getId()).orElseThrow().getEspaciosDisponibles();
    }

    private EstadoReserva estadoDe(Reserva reserva) {
        return reservaRepository.findById(reserva.getId()).orElseThrow().getEstado();
    }

    @Test
    void cancelarUnaAceptadaLaDejaCanceladaYDevuelveElCupo() throws Exception {
        Reserva reserva = reservaDe(ana, EstadoReserva.ACEPTADA);

        mockMvc.perform(post("/reserva/cancelar/" + reserva.getId()).with(csrf()).cookie(entrar("ana@correo.com")))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.CANCELADA, estadoDe(reserva));
        assertEquals(5, cuposLibres(), "El cupo de la reserva aceptada vuelve al parqueadero");
    }

    @Test
    void cancelarUnaPendienteNoTocaLosCupos() throws Exception {
        Reserva reserva = reservaDe(ana, EstadoReserva.PENDIENTE);

        mockMvc.perform(post("/reserva/cancelar/" + reserva.getId()).with(csrf()).cookie(entrar("ana@correo.com")))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.CANCELADA, estadoDe(reserva));
        assertEquals(4, cuposLibres());
    }

    @Test
    void laReservaDeOtroClienteRespondeNoEncontrada() throws Exception {
        Reserva reservaDeAna = reservaDe(ana, EstadoReserva.ACEPTADA);

        mockMvc.perform(post("/reserva/cancelar/" + reservaDeAna.getId()).with(csrf()).cookie(entrar("beto@correo.com")))
                .andExpect(status().isNotFound());

        assertEquals(EstadoReserva.ACEPTADA, estadoDe(reservaDeAna));
        assertEquals(4, cuposLibres());
    }

    @Test
    void elCambioAtomicoNoModificaNadaSiElEstadoYaNoEsElEsperado() {
        Reserva reserva = reservaDe(ana, EstadoReserva.RECHAZADA);

        long modificadas = reservaRepository.cambiarEstadoSi(reserva.getId(), EstadoReserva.ACEPTADA, EstadoReserva.CANCELADA);

        assertEquals(0, modificadas);
        assertEquals(EstadoReserva.RECHAZADA, estadoDe(reserva));
    }

    @Test
    void cancelarNoExigeTenerElPerfilCompleto() throws Exception {
        // Sin placa: el interceptor de US-04 lo frenaría al reservar, pero no al cancelar.
        Usuario sinPlaca = usuarioService.registrarUsuario("Caro", "1000000003", "caro@correo.com", "clave1234", null, Rol.CLIENTE);
        Reserva reserva = reservaDe(sinPlaca, EstadoReserva.PENDIENTE);

        mockMvc.perform(post("/reserva/cancelar/" + reserva.getId()).with(csrf()).cookie(entrar("caro@correo.com")))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.CANCELADA, estadoDe(reserva));
    }

    @Test
    void elPanelMuestraElBotonSoloEnReservasCancelables() throws Exception {
        Reserva aceptada = reservaDe(ana, EstadoReserva.ACEPTADA);
        Reserva utilizada = reservaDe(ana, EstadoReserva.UTILIZADA);

        String html = mockMvc.perform(get("/cliente").cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("/reserva/cancelar/" + aceptada.getId()));
        assertTrue(!html.contains("/reserva/cancelar/" + utilizada.getId()));
    }
}
