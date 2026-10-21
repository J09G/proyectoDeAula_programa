package com.proyecto.parking.controller;

import com.proyecto.parking.model.Pago;
import com.proyecto.parking.model.Pago.EstadoPago;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.model.Zona;
import com.proyecto.parking.repository.PagoRepository;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.RolRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import com.proyecto.parking.security.JwtService;
import com.proyecto.parking.service.PasarelaPago;
import com.proyecto.parking.service.PasarelaPago.Transaccion;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anticipo al reservar, de punta a punta contra el Mongo embebido y con la
 * pasarela simulada: reservar, pagar, que el parqueadero decida, y cobrar el
 * saldo a la salida. También comprueba que las pantallas se muestran sin error.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.data.mongodb.uri=mongodb://localhost:27017/parking-test-no-se-usa",
                "spring.data.mongodb.database=parking-test",
                "brevo.api.key=test-key-no-se-usa",
                "de.flapdoodle.mongodb.embedded.version=7.0.5",
                // Con el access token de Mercado Pago los pagos quedan activos.
                "parking.pagos.mercado-pago.access-token=TEST-prueba"

        })
@AutoConfigureMockMvc
class PagoAnticipoIT {

    private static final String URL_PAGO = "https://www.mercadopago.com.co/checkout/v1/redirect?pref_id=prueba";

    @Autowired private MockMvc mockMvc;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ZonaRepository zonaRepository;
    @Autowired private ParqueaderoRepository parqueaderoRepository;
    @Autowired private ReservaRepository reservaRepository;
    @Autowired private RegistroParqueoRepository registroRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private UsuarioService usuarioService;

    /** La pasarela real no se toca: aquí se decide qué "responde Mercado Pago". */
    @MockitoBean private PasarelaPago pasarela;

    private Usuario ana;
    private Parqueadero parqueadero;

    @BeforeEach
    void preparar() {
        pagoRepository.deleteAll();
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
        usuarioService.registrarUsuario("Beto Ruiz", "1000000002", "beto@correo.com", "clave1234", "XYZ987", Rol.CLIENTE);
        Usuario admin = usuarioService.registrarUsuario("Admin", "2000000001", "admin@correo.com", "clave1234", null, Rol.ADMINISTRADOR);
        usuarioService.registrarUsuario("Jefa", "3000000001", "super@correo.com", "clave1234", null, Rol.SUPERADMIN);

        Zona zona = new Zona();
        zona.setNombreZona("Centro");
        zona = zonaRepository.save(zona);

        parqueadero = new Parqueadero();
        parqueadero.setNombre("Central");
        parqueadero.setDireccion("Calle 1");
        parqueadero.setTarifaHora(5_000d);
        parqueadero.setEspaciosTotales(10);
        parqueadero.setEspaciosDisponibles(4);
        parqueadero.setHabilitado(true);
        parqueadero.setZona(zona);
        parqueadero.setAdministrador(admin);
        parqueadero = parqueaderoRepository.save(parqueadero);

        when(pasarela.urlDePago(any(), anyString())).thenReturn(URL_PAGO);
        when(pasarela.nombre()).thenReturn("Mercado Pago");
    }

    private Cookie entrar(String correo) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("email", correo).param("password", "clave1234"))
                .andReturn().getResponse().getCookie(JwtService.NOMBRE_COOKIE);
    }

    /** Ana reserva el cubículo 3 por el formulario y devuelve la reserva creada. */
    private Reserva anaReserva() throws Exception {
        String llegada = LocalDateTime.now().plusHours(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        mockMvc.perform(post("/reserva/crear").with(csrf()).cookie(entrar("ana@correo.com"))
                        .param("idParqueadero", parqueadero.getId())
                        .param("espacioReservado", "3")
                        .param("fechaReserva", llegada))
                .andExpect(status().is3xxRedirection());
        List<Reserva> reservas = reservaRepository.findAll();
        assertEquals(1, reservas.size());
        return reservas.get(0);
    }

    /** Ana pulsa "Pagar" y Mercado Pago aprueba (o no) ese intento. */
    private Pago anaPaga(Reserva reserva, String estadoEnLaPasarela) throws Exception {
        mockMvc.perform(get("/reserva/pago/" + reserva.getId() + "/ir").cookie(entrar("ana@correo.com")))
                .andExpect(redirectedUrl(URL_PAGO));
        Pago pago = pagoRepository.findByIdReserva(reserva.getId()).get(0);
        when(pasarela.consultarTransaccion("txn-1")).thenReturn(Optional.of(new Transaccion(
                "txn-1", pago.getReferencia(), estadoEnLaPasarela, pago.getMontoCentavos(), "COP", "CARD")));
        // El cliente vuelve de la pasarela; esta ruta no exige sesión.
        mockMvc.perform(get("/pagos/retorno").param("payment_id", "txn-1"))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));
        return pagoRepository.findById(pago.getId()).orElseThrow();
    }

    private Reserva recargar(Reserva reserva) {
        return reservaRepository.findById(reserva.getId()).orElseThrow();
    }

    // ── Reservar ─────────────────────────────────────────────────────────────

    @Test
    void elFormularioAvisaDelAnticipoAntesDeReservar() throws Exception {
        mockMvc.perform(get("/reserva/" + parqueadero.getId()).cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$5.000")))
                .andExpect(content().string(containsString("Reservar y pagar anticipo")));
    }

    @Test
    void reservarLlevaAPagarYDejaElCubiculoApartado() throws Exception {
        Reserva reserva = anaReserva();

        assertEquals(EstadoReserva.PENDIENTE_PAGO, reserva.getEstado());
        assertEquals(5_000d, reserva.getAnticipo());
        assertEquals(4, parqueaderoRepository.findById(parqueadero.getId()).orElseThrow().getEspaciosDisponibles(),
                "Apartar el cubículo no descuenta cupo");

        // La página de pago se muestra con el importe y el botón.
        mockMvc.perform(get("/reserva/pago/" + reserva.getId()).cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("$5.000")))
                .andExpect(content().string(containsString("Pagar con Mercado Pago")));

        // Mientras Ana paga, Beto no puede pedir el mismo cubículo.
        mockMvc.perform(post("/reserva/crear").with(csrf()).cookie(entrar("beto@correo.com"))
                        .param("idParqueadero", parqueadero.getId())
                        .param("espacioReservado", "3")
                        .param("fechaReserva", LocalDateTime.now().plusHours(3)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))))
                .andExpect(redirectedUrl("/reserva/" + parqueadero.getId()));
        assertEquals(1, reservaRepository.count());
    }

    @Test
    void laPaginaDePagoDeOtroClienteNoExiste() throws Exception {
        Reserva reserva = anaReserva();

        mockMvc.perform(get("/reserva/pago/" + reserva.getId()).cookie(entrar("beto@correo.com")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/reserva/pago/" + reserva.getId() + "/ir").cookie(entrar("beto@correo.com")))
                .andExpect(status().isNotFound());
        assertEquals(0, pagoRepository.count());
    }

    @Test
    void misReservasOfrecePagarLaQueEstaPendiente() throws Exception {
        anaReserva();

        mockMvc.perform(get("/cliente").cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pendiente de pago")))
                .andExpect(content().string(containsString("Pagar anticipo")));
    }

    // ── Pagar ────────────────────────────────────────────────────────────────

    @Test
    void pagoAprobadoDejaLaReservaEnManosDelParqueadero() throws Exception {
        Reserva reserva = anaReserva();

        Pago pago = anaPaga(reserva, "APPROVED");

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertEquals("txn-1", pago.getIdTransaccion());
        assertEquals(EstadoReserva.PENDIENTE, recargar(reserva).getEstado());

        mockMvc.perform(get("/cliente").cookie(entrar("ana@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pagado")))
                .andExpect(content().string(not(containsString("Pagar anticipo"))));

        // El administrador ve la solicitud con el anticipo ya cobrado.
        mockMvc.perform(get("/admin/parqueadero/" + parqueadero.getId()).cookie(entrar("admin@correo.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anticipo pagado: $5.000")));
    }

    @Test
    void pagoRechazadoNoConfirmaLaReserva() throws Exception {
        Reserva reserva = anaReserva();

        Pago pago = anaPaga(reserva, "DECLINED");

        assertEquals(EstadoPago.RECHAZADO, pago.getEstado());
        assertEquals(EstadoReserva.PENDIENTE_PAGO, recargar(reserva).getEstado());
    }

    @Test
    void elRetornoSinTransaccionNoRompeNada() throws Exception {
        Reserva reserva = anaReserva();

        mockMvc.perform(get("/pagos/retorno")).andExpect(redirectedUrl("/cliente?scroll=reservas"));
        mockMvc.perform(get("/pagos/retorno").param("payment_id", "inventada"))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));
        // Si el cliente abandona el pago, Mercado Pago devuelve payment_id=null.
        mockMvc.perform(get("/pagos/retorno").param("payment_id", "null"))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.PENDIENTE_PAGO, recargar(reserva).getEstado());
    }

    @Test
    void elAvisoDeLaPasarelaEntraSinSesionNiTokenCsrf() throws Exception {
        // Sin firma válida se descarta, pero la ruta responde 200 y no 403.
        mockMvc.perform(post("/pagos/eventos").param("type", "payment").param("data.id", "123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"action\":\"payment.updated\",\"data\":{\"id\":\"123\"}}"))
                .andExpect(status().isOk());
    }

    // ── Después de pagar ─────────────────────────────────────────────────────

    @Test
    void siElParqueaderoRechazaSeDevuelveElAnticipo() throws Exception {
        Reserva reserva = anaReserva();
        Pago pago = anaPaga(reserva, "APPROVED");
        when(pasarela.devolver("txn-1")).thenReturn(true);

        mockMvc.perform(post("/admin/parqueadero/" + parqueadero.getId() + "/reserva/rechazar/" + reserva.getId())
                        .with(csrf()).cookie(entrar("admin@correo.com")))
                .andExpect(status().is3xxRedirection());

        assertEquals(EstadoReserva.RECHAZADA, recargar(reserva).getEstado());
        assertEquals(EstadoPago.DEVUELTO, pagoRepository.findById(pago.getId()).orElseThrow().getEstado());
    }

    @Test
    void cancelarConTiempoDevuelveElAnticipo() throws Exception {
        Reserva reserva = anaReserva(); // llegada dentro de 3 horas
        Pago pago = anaPaga(reserva, "APPROVED");
        // La pasarela no puede anularlo sola: queda para devolver a mano, no se pierde.
        when(pasarela.devolver("txn-1")).thenReturn(false);

        mockMvc.perform(post("/reserva/cancelar/" + reserva.getId()).with(csrf()).cookie(entrar("ana@correo.com")))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.CANCELADA, recargar(reserva).getEstado());
        assertEquals(EstadoPago.DEVOLUCION_PENDIENTE, pagoRepository.findById(pago.getId()).orElseThrow().getEstado());
    }

    @Test
    void cancelarAUltimaHoraUnaReservaAceptadaPierdeElAnticipo() throws Exception {
        Reserva reserva = anaReserva();
        Pago pago = anaPaga(reserva, "APPROVED");
        // El parqueadero ya la aceptó: le guardó el cubículo a Ana.
        mockMvc.perform(post("/admin/parqueadero/" + parqueadero.getId() + "/reserva/aceptar/" + reserva.getId())
                        .with(csrf()).cookie(entrar("admin@correo.com")))
                .andExpect(status().is3xxRedirection());
        assertEquals(EstadoReserva.ACEPTADA, recargar(reserva).getEstado());
        // Faltan 20 minutos para la llegada: fuera del plazo de devolución (60 min).
        Reserva guardada = recargar(reserva);
        guardada.setFechaReserva(LocalDateTime.now().plusMinutes(20));
        reservaRepository.save(guardada);

        mockMvc.perform(post("/reserva/cancelar/" + reserva.getId()).with(csrf()).cookie(entrar("ana@correo.com")))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.CANCELADA, recargar(reserva).getEstado());
        assertEquals(EstadoPago.RETENIDO, pagoRepository.findById(pago.getId()).orElseThrow().getEstado());
    }

    // ── Devoluciones manuales ────────────────────────────────────────────────

    @Test
    void laDevolucionQueLaPasarelaNoPudoHacerLaVeYLaCierraElSuperadministrador() throws Exception {
        Reserva reserva = anaReserva();
        Pago pago = anaPaga(reserva, "APPROVED");
        // Mercado Pago no pudo devolverlo (por ejemplo, sin saldo en la cuenta).
        when(pasarela.devolver("txn-1")).thenReturn(false);

        mockMvc.perform(post("/admin/parqueadero/" + parqueadero.getId() + "/reserva/rechazar/" + reserva.getId())
                        .with(csrf()).cookie(entrar("admin@correo.com")))
                .andExpect(status().is3xxRedirection());
        assertEquals(EstadoPago.DEVOLUCION_PENDIENTE, pagoRepository.findById(pago.getId()).orElseThrow().getEstado());

        Cookie jefa = entrar("super@correo.com");
        mockMvc.perform(get("/superadmin/devoluciones").cookie(jefa))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Torres")))
                .andExpect(content().string(containsString("1000000001")))
                .andExpect(content().string(containsString("$5.000")))
                .andExpect(content().string(containsString(pago.getReferencia())));

        mockMvc.perform(post("/superadmin/devoluciones/" + pago.getId() + "/devuelto").with(csrf()).cookie(jefa))
                .andExpect(redirectedUrl("/superadmin/devoluciones"));

        assertEquals(EstadoPago.DEVUELTO, pagoRepository.findById(pago.getId()).orElseThrow().getEstado());
        // Ya no aparece en la lista.
        mockMvc.perform(get("/superadmin/devoluciones").cookie(jefa))
                .andExpect(content().string(not(containsString(pago.getReferencia()))));
    }

    @Test
    void elParqueaderoNoPuedeVerNiMarcarDevoluciones() throws Exception {
        Cookie admin = entrar("admin@correo.com");

        mockMvc.perform(get("/superadmin/devoluciones").cookie(admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/superadmin/devoluciones/cualquiera/devuelto").with(csrf()).cookie(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelarAUltimaHoraUnaReservaSinAceptarDevuelveElAnticipo() throws Exception {
        Reserva reserva = anaReserva();
        Pago pago = anaPaga(reserva, "APPROVED");
        when(pasarela.devolver("txn-1")).thenReturn(true);
        // Faltan 20 minutos y el parqueadero todavía no ha respondido.
        Reserva guardada = recargar(reserva);
        guardada.setFechaReserva(LocalDateTime.now().plusMinutes(20));
        reservaRepository.save(guardada);

        mockMvc.perform(post("/reserva/cancelar/" + reserva.getId()).with(csrf()).cookie(entrar("ana@correo.com")))
                .andExpect(redirectedUrl("/cliente?scroll=reservas"));

        assertEquals(EstadoReserva.CANCELADA, recargar(reserva).getEstado());
        assertEquals(EstadoPago.DEVUELTO, pagoRepository.findById(pago.getId()).orElseThrow().getEstado());
    }

    @Test
    void elAnticipoSeDescuentaDeLaFacturaAlSalir() throws Exception {
        Reserva reserva = anaReserva();
        anaPaga(reserva, "APPROVED");
        Cookie admin = entrar("admin@correo.com");
        String base = "/admin/parqueadero/" + parqueadero.getId();

        mockMvc.perform(post(base + "/reserva/aceptar/" + reserva.getId()).with(csrf()).cookie(admin))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post(base + "/registros/entrada").with(csrf()).cookie(admin)
                        .param("placa", "ABC123").param("cedula", "1000000001").param("espacioReservado", "3"))
                .andExpect(status().is3xxRedirection());

        RegistroParqueo registro = registroRepository.findAll().get(0);
        assertEquals(5_000d, registro.getAnticipo(), "El anticipo viaja de la reserva al registro");

        mockMvc.perform(post(base + "/registros/salida/" + registro.getId()).with(csrf()).cookie(admin))
                .andExpect(status().is3xxRedirection());

        RegistroParqueo finalizado = registroRepository.findById(registro.getId()).orElseThrow();
        // Estancia de menos de una hora: se factura la hora mínima, ya cubierta por el anticipo.
        assertEquals(5_000d, finalizado.getValorPagado());
        assertEquals(0d, finalizado.getSaldoAPagar());

        mockMvc.perform(get(base + "/registros/factura/" + registro.getId()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anticipo pagado al reservar")))
                .andExpect(content().string(containsString("Saldo a pagar")));
        assertTrue(mockMvc.perform(get(base + "/registros/factura/pdf/" + registro.getId()).cookie(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray().length > 500, "El PDF se genera");
    }
}
