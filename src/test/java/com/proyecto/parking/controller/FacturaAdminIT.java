package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RegistroParqueoRepository;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La factura del administrador (vista y PDF) en los distintos estados de un registro. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class FacturaAdminIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private RegistroParqueoRepository registroRepository;
    @Autowired private UsuarioService usuarioService;

    private Usuario ana;
    private Parqueadero parqueadero;

    @BeforeEach
    void preparar() {
        registroRepository.deleteAll();
        reservaRepository.deleteAll();
        parqueaderoRepository.deleteAll();
        zonaRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        rolRepository.save(new Rol(Rol.CLIENTE));
        rolRepository.save(new Rol(Rol.ADMINISTRADOR));
        rolRepository.save(new Rol(Rol.SUPERADMIN));

        ana = usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);
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

    private RegistroParqueo finalizado(Usuario cliente, Reserva reserva) {
        RegistroParqueo r = new RegistroParqueo();
        r.setPlaca("ABC123");
        r.setUsuario(cliente);
        r.setParqueadero(parqueadero);
        r.setReserva(reserva);
        r.setEspacioReservado(3);
        r.setHoraEntrada(LocalDateTime.now().minusMinutes(90));
        r.setHoraSalida(LocalDateTime.now());
        r.setTiempoMinutos(90L);
        r.setValorPagado(4500.0);
        r.setTarifaHoraAplicada(3000.0);
        r.setEstado(EstadoRegistro.FINALIZADO);
        return registroRepository.save(r);
    }

    private Reserva reservaDeAna() {
        Reserva reserva = new Reserva(EstadoReserva.ACEPTADA, ana, parqueadero);
        reserva.setEspacioReservado(3);
        reserva.setFechaReserva(LocalDateTime.now().minusHours(2));
        return reservaRepository.save(reserva);
    }

    private Cookie admin() throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", "admin@correo.com").param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    private void verFacturaYPdf(RegistroParqueo registro) throws Exception {
        String base = "/admin/parqueadero/" + parqueadero.getId() + "/registros/factura/";
        Cookie sesion = admin();
        mockMvc.perform(get(base + registro.getId()).cookie(sesion)).andExpect(status().isOk());
        mockMvc.perform(get(base + "pdf/" + registro.getId()).cookie(sesion)).andExpect(status().isOk());
    }

    @Test
    void facturaDeUnParqueoConReserva() throws Exception {
        verFacturaYPdf(finalizado(ana, reservaDeAna()));
    }

    @Test
    void facturaDeUnParqueoSinReserva() throws Exception {
        verFacturaYPdf(finalizado(ana, null));
    }

    @Test
    void facturaDeUnClienteDePaso() throws Exception {
        verFacturaYPdf(finalizado(null, null));
    }

    @Test
    void facturaCuandoLaReservaYaFueEliminada() throws Exception {
        Reserva reserva = reservaDeAna();
        RegistroParqueo registro = finalizado(ana, reserva);
        reservaRepository.deleteById(reserva.getId());

        verFacturaYPdf(registro);
    }

    @Test
    void facturaCuandoElClienteYaFueEliminado() throws Exception {
        RegistroParqueo registro = finalizado(ana, null);
        usuarioRepository.deleteById(ana.getId());

        verFacturaYPdf(registro);
    }
}
