package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
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

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Servicio web del módulo predictivo y las pantallas que lo muestran. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5"
        })
@AutoConfigureMockMvc
class PrediccionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private RegistroParqueoRepository registroRepository;
    @Autowired private UsuarioService usuarioService;

    private Parqueadero parqueadero;
    private final LocalDate manana = LocalDate.now().plusDays(1);

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

        usuarioService.registrarUsuario("Ana Torres", "1000000001", "ana@correo.com", "clave1234", "ABC123", Rol.CLIENTE);
        Usuario admin = usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);

        Zona zona = new Zona();
        zona.setNombreZona("Centro Histórico");
        zona = zonaRepository.save(zona);

        parqueadero = new Parqueadero();
        parqueadero.setNombre("Central");
        parqueadero.setDireccion("Calle 1");
        parqueadero.setHorario("6:00 - 22:00");
        parqueadero.setTarifaHora(5_000d);
        parqueadero.setEspaciosTotales(10);
        parqueadero.setEspaciosDisponibles(10);
        parqueadero.setHabilitado(true);
        parqueadero.setZona(zona);
        parqueadero.setAdministrador(admin);
        parqueadero = parqueaderoRepository.save(parqueadero);
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    @Test
    void devuelveUnaEstimacionPorCadaFranjaDelDia() throws Exception {
        mockMvc.perform(get("/prediccion/dia").cookie(entrar("ana@correo.com"))
                        .param("idParqueadero", parqueadero.getId()).param("fecha", manana.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas", hasSize(17)))
                .andExpect(jsonPath("$.franjas[0].hora", is(6)))
                .andExpect(jsonPath("$.franjas[16].hora", is(22)))
                .andExpect(jsonPath("$.franjas[5].porcentaje", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.franjas[5].porcentaje", lessThanOrEqualTo(100)))
                // Sin registros de la semana anterior se avisa de que se usó el perfil de la zona.
                .andExpect(jsonPath("$.conHistorial", is(false)));
    }

    @Test
    void conRegistrosDeLaSemanaAnteriorUsaElHistorialDelParqueadero() throws Exception {
        // Hace una semana el parqueadero estuvo lleno toda la tarde.
        for (int i = 1; i <= 10; i++) {
            RegistroParqueo registro = new RegistroParqueo();
            registro.setPlaca("AAA00" + (i % 10));
            registro.setEspacioReservado(i);
            registro.setParqueadero(parqueadero);
            registro.setHoraEntrada(manana.minusDays(7).atTime(14, 0));
            registro.setHoraSalida(manana.minusDays(7).atTime(20, 0));
            registro.setEstado(RegistroParqueo.EstadoRegistro.FINALIZADO);
            registroRepository.save(registro);
        }

        mockMvc.perform(get("/prediccion/dia").cookie(entrar("ana@correo.com"))
                        .param("idParqueadero", parqueadero.getId()).param("fecha", manana.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conHistorial", is(true)))
                // Las 17:00 (lleno la semana pasada) se esperan más ocupadas que las 8:00 (vacío).
                .andExpect(jsonPath("$.franjas[11].porcentaje", greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$.franjas[2].porcentaje", lessThanOrEqualTo(49)));
    }

    @Test
    void laFranjaDeUnaHoraConcretaTraeSuNivel() throws Exception {
        mockMvc.perform(get("/prediccion/ocupacion").cookie(entrar("admin@correo.com"))
                        .param("idParqueadero", parqueadero.getId()).param("fecha", manana + "T18:30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hora", is(18)))
                .andExpect(jsonPath("$.nivel").exists());
    }

    @Test
    void rechazaFechasMalEscritasOLejanas() throws Exception {
        Cookie ana = entrar("ana@correo.com");
        mockMvc.perform(get("/prediccion/dia").cookie(ana)
                        .param("idParqueadero", parqueadero.getId()).param("fecha", "mañana"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/prediccion/dia").cookie(ana)
                        .param("idParqueadero", parqueadero.getId()).param("fecha", manana.plusYears(2).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinSesionNoSePuedeConsultar() throws Exception {
        mockMvc.perform(get("/prediccion/dia")
                        .param("idParqueadero", parqueadero.getId()).param("fecha", manana.toString()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void lasPantallasDeReservaYDelAdministradorMuestranElModulo() throws Exception {
        mockMvc.perform(get("/reserva/" + parqueadero.getId()).cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-prediccion")))
                .andExpect(content().string(containsString("/js/prediccion.js")));
        mockMvc.perform(get("/admin/parqueadero/" + parqueadero.getId()).cookie(entrar("admin@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ocupación prevista")))
                .andExpect(content().string(containsString("data-prediccion")));
    }
}
