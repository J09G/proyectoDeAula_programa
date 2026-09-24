package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.dto.ReservaForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.EmailService;
import com.proyecto.parking.service.EspacioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservaServiceImplTest {

    private static final String ID_CLIENTE = "cli-1";
    private static final String ID_PARQUEADERO = "parq-1";
    private static final String ID_ADMIN = "admin-1";
    private static final String ID_RESERVA = "res-1";

    @Mock private ReservaRepository reservaRepository;
    @Mock private RegistroParqueoRepository registroParqueoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UsuarioService usuarioService;
    @Mock private ParqueaderoService parqueaderoService;
    @Mock private EspacioService espacioService;
    @Mock private EmailService emailService;

    private ReservaServiceImpl servicio;

    private Usuario cliente;
    private Parqueadero parqueadero;

    @BeforeEach
    void preparar() {
        ParkingProperties properties = new ParkingProperties();
        properties.getReservas().setMinutosTolerancia(120);

        servicio = new ReservaServiceImpl(reservaRepository, registroParqueoRepository,
                usuarioRepository, usuarioService, parqueaderoService, espacioService,
                emailService, properties);

        cliente = new Usuario();
        cliente.setId(ID_CLIENTE);
        cliente.setNombre("Ana");
        cliente.setCorreo("ana@test.com");

        parqueadero = new Parqueadero();
        parqueadero.setId(ID_PARQUEADERO);
        parqueadero.setNombre("Central");
        parqueadero.setEspaciosTotales(10);
        parqueadero.setEspaciosDisponibles(5);
        parqueadero.setHabilitado(true);
        Usuario admin = new Usuario();
        admin.setId(ID_ADMIN);
        parqueadero.setAdministrador(admin);

        when(usuarioService.obtenerUsuarioPorId(ID_CLIENTE)).thenReturn(cliente);
        when(parqueaderoService.obtenerParqueaderoPorId(ID_PARQUEADERO)).thenReturn(parqueadero);
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReservaForm formulario(int espacio) {
        ReservaForm form = new ReservaForm();
        form.setIdParqueadero(ID_PARQUEADERO);
        form.setEspacioReservado(espacio);
        form.setFechaReserva(LocalDateTime.now().plusHours(2));
        return form;
    }

    private Reserva reservaPendiente(int espacio) {
        Reserva reserva = new Reserva();
        reserva.setId(ID_RESERVA);
        reserva.setCliente(cliente);
        reserva.setParqueadero(parqueadero);
        reserva.setEspacioReservado(espacio);
        reserva.setEstado(EstadoReserva.PENDIENTE);
        reserva.setFechaReserva(LocalDateTime.now().plusHours(1));
        return reserva;
    }

    // ── Crear ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Crear reserva")
    class Crear {

        @Test
        @DisplayName("una reserva válida nace en estado PENDIENTE y no consume cupo todavía")
        void reservaValida() {
            Reserva creada = servicio.crearReserva(ID_CLIENTE, formulario(3));

            assertThat(creada.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
            assertThat(creada.getEspacioReservado()).isEqualTo(3);
            // El cupo se descuenta al aceptar, no al solicitar.
            verify(parqueaderoService, never()).ocuparEspacio(anyString());
        }

        @Test
        @DisplayName("rechaza un cubículo que no existe en el parqueadero")
        void cubiculoFueraDeRango() {
            assertThatThrownBy(() -> servicio.crearReserva(ID_CLIENTE, formulario(11)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no existe");
        }

        @Test
        @DisplayName("rechaza un cubículo ya comprometido aunque el formulario lo enviase")
        void cubiculoComprometido() {
            when(espacioService.estaComprometido(ID_PARQUEADERO, 3)).thenReturn(true);

            assertThatThrownBy(() -> servicio.crearReserva(ID_CLIENTE, formulario(3)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("ya no está disponible");
        }

        @Test
        @DisplayName("no deja reservar con un vehículo ya dentro")
        void conVehiculoDentro() {
            when(registroParqueoRepository.findByUsuario_IdAndEstado(ID_CLIENTE, EstadoRegistro.ACTIVO))
                    .thenReturn(List.of(new com.proyecto.parking.model.RegistroParqueo()));

            assertThatThrownBy(() -> servicio.crearReserva(ID_CLIENTE, formulario(3)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("vehículo dentro");
        }

        @Test
        @DisplayName("no deja duplicar reserva abierta en el mismo parqueadero")
        void reservaDuplicada() {
            when(reservaRepository.findByCliente_IdAndEstadoIn(eq(ID_CLIENTE), any()))
                    .thenReturn(List.of(reservaPendiente(7)));

            assertThatThrownBy(() -> servicio.crearReserva(ID_CLIENTE, formulario(3)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("Ya tienes una reserva activa");
        }

        @Test
        @DisplayName("un parqueadero deshabilitado no acepta reservas")
        void parqueaderoDeshabilitado() {
            parqueadero.setHabilitado(false);

            assertThatThrownBy(() -> servicio.crearReserva(ID_CLIENTE, formulario(3)))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no está aceptando reservas");
        }
    }

    // ── Aceptar / rechazar ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Aceptar y rechazar")
    class Decidir {

        @Test
        @DisplayName("aceptar descuenta el cupo y deja la reserva ACEPTADA")
        void aceptarDescuentaCupo() {
            Reserva reserva = reservaPendiente(3);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));

            Reserva resultado = servicio.aceptarReserva(ID_RESERVA, ID_ADMIN);

            assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.ACEPTADA);
            verify(parqueaderoService).ocuparEspacio(ID_PARQUEADERO);
            verify(emailService).enviarCorreo(eq("ana@test.com"), anyString(), anyString());
        }

        @Test
        @DisplayName("si el guardado falla tras tomar el cupo, el cupo se devuelve")
        void compensaElCupoSiFallaElGuardado() {
            Reserva reserva = reservaPendiente(3);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
            when(reservaRepository.save(any(Reserva.class)))
                    .thenThrow(new IllegalStateException("fallo de red"));

            assertThatThrownBy(() -> servicio.aceptarReserva(ID_RESERVA, ID_ADMIN))
                    .isInstanceOf(IllegalStateException.class);

            // Sin esta compensación el parqueadero perdería un cupo para siempre.
            verify(parqueaderoService).liberarEspacio(ID_PARQUEADERO);
        }

        @Test
        @DisplayName("no se acepta dos veces el mismo cubículo")
        void cubiculoYaAceptado() {
            Reserva reserva = reservaPendiente(3);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
            when(reservaRepository.findByParqueadero_IdAndEspacioReservadoAndEstado(
                    ID_PARQUEADERO, 3, EstadoReserva.ACEPTADA))
                    .thenReturn(List.of(new Reserva()));

            assertThatThrownBy(() -> servicio.aceptarReserva(ID_RESERVA, ID_ADMIN))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("ya fue asignado");

            verify(parqueaderoService, never()).ocuparEspacio(anyString());
        }

        @Test
        @DisplayName("sólo se gestionan reservas pendientes")
        void soloPendientes() {
            Reserva reserva = reservaPendiente(3);
            reserva.setEstado(EstadoReserva.UTILIZADA);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));

            assertThatThrownBy(() -> servicio.aceptarReserva(ID_RESERVA, ID_ADMIN))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("pendientes");
        }

        @Test
        @DisplayName("rechazar no toca el inventario de cupos")
        void rechazarNoTocaCupos() {
            Reserva reserva = reservaPendiente(3);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));

            Reserva resultado = servicio.rechazarReserva(ID_RESERVA, ID_ADMIN);

            assertThat(resultado.getEstado()).isEqualTo(EstadoReserva.RECHAZADA);
            verify(parqueaderoService, never()).ocuparEspacio(anyString());
            verify(parqueaderoService, never()).liberarEspacio(anyString());
        }
    }

    // ── Eliminar ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Eliminar reserva")
    class Eliminar {

        @Test
        @DisplayName("eliminar una ACEPTADA devuelve el cubículo al inventario")
        void aceptadaDevuelveCupo() {
            Reserva reserva = reservaPendiente(3);
            reserva.setEstado(EstadoReserva.ACEPTADA);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));

            servicio.eliminarReserva(ID_RESERVA, ID_ADMIN);

            verify(parqueaderoService).liberarEspacio(ID_PARQUEADERO);
            verify(reservaRepository).deleteById(ID_RESERVA);
        }

        @Test
        @DisplayName("eliminar una PENDIENTE no devuelve nada, porque nunca tomó cupo")
        void pendienteNoDevuelveCupo() {
            Reserva reserva = reservaPendiente(3);
            when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));

            servicio.eliminarReserva(ID_RESERVA, ID_ADMIN);

            verify(parqueaderoService, never()).liberarEspacio(anyString());
            verify(reservaRepository).deleteById(ID_RESERVA);
        }
    }

    // ── Expiración ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Expiración automática")
    class Expiracion {

        @Test
        @DisplayName("marca EXPIRADA y libera el cubículo de quien no se presentó")
        void expiraYLibera() {
            Reserva vencida = reservaPendiente(3);
            vencida.setEstado(EstadoReserva.ACEPTADA);
            vencida.setFechaReserva(LocalDateTime.now().minusHours(5));

            when(reservaRepository.findByEstadoAndFechaReservaBefore(eq(EstadoReserva.ACEPTADA), any()))
                    .thenReturn(List.of(vencida));

            int expiradas = servicio.expirarReservasVencidas();

            assertThat(expiradas).isEqualTo(1);
            assertThat(vencida.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
            verify(parqueaderoService).liberarEspacio(ID_PARQUEADERO);
        }

        @Test
        @DisplayName("una reserva problemática no impide expirar las demás")
        void unFalloNoDetieneElResto() {
            Parqueadero otro = new Parqueadero();
            otro.setId("parq-2");
            otro.setNombre("Norte");

            Reserva mala = reservaPendiente(3);
            mala.setId("res-mala");
            mala.setEstado(EstadoReserva.ACEPTADA);
            mala.setParqueadero(otro);

            Reserva buena = reservaPendiente(4);
            buena.setId("res-buena");
            buena.setEstado(EstadoReserva.ACEPTADA);

            when(reservaRepository.findByEstadoAndFechaReservaBefore(eq(EstadoReserva.ACEPTADA), any()))
                    .thenReturn(List.of(mala, buena));
            doThrow(new IllegalStateException("boom"))
                    .when(parqueaderoService).liberarEspacio("parq-2");

            int expiradas = servicio.expirarReservasVencidas();

            // La que falla se registra en el log y el bucle sigue con la siguiente.
            assertThat(expiradas).isEqualTo(1);
            assertThat(buena.getEstado()).isEqualTo(EstadoReserva.EXPIRADA);
            verify(parqueaderoService).liberarEspacio(ID_PARQUEADERO);
        }

        @Test
        @DisplayName("sin reservas vencidas no hace nada")
        void sinVencidas() {
            when(reservaRepository.findByEstadoAndFechaReservaBefore(eq(EstadoReserva.ACEPTADA), any()))
                    .thenReturn(List.of());

            assertThat(servicio.expirarReservasVencidas()).isZero();
            verify(parqueaderoService, never()).liberarEspacio(anyString());
        }
    }
}
