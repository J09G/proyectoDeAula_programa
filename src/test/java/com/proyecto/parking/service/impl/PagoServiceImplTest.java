package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Pago;
import com.proyecto.parking.model.Pago.EstadoPago;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.PagoRepository;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.PagoService.DevolucionPendiente;
import com.proyecto.parking.service.PagoService.Resultado;
import com.proyecto.parking.service.PasarelaPago;
import com.proyecto.parking.service.PasarelaPago.Transaccion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Pago del anticipo")
class PagoServiceImplTest {

    private static final String ID_CLIENTE = "cli-1";
    private static final String ID_RESERVA = "665f1c2a9b3e4d0012ab34cd";
    private static final String REFERENCIA = "PKG-AB34CD-0011223344556677";
    private static final long CENTAVOS = 500_000; // 5.000 COP

    @Mock private PagoRepository pagoRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ParqueaderoRepository parqueaderoRepository;
    @Mock private PasarelaPago pasarela;

    private ParkingProperties properties;
    private PagoServiceImpl servicio;
    private Reserva reserva;
    private Pago pago;

    @BeforeEach
    void preparar() {
        properties = new ParkingProperties();
        properties.getPagos().getWompi().setLlavePublica("pub_test_abc");
        properties.getPagos().getWompi().setSecretoIntegridad("test_integrity_abc");
        properties.getPagos().setMinutosParaPagar(15);
        properties.getPagos().setMinutosAntelacionDevolucion(60);
        properties.getPagos().setMontoMinimo(1_500);
        properties.setUrlPublica("https://parking.test");

        servicio = new PagoServiceImpl(pagoRepository, reservaRepository, usuarioRepository,
                parqueaderoRepository, pasarela, properties);

        Usuario cliente = new Usuario();
        cliente.setId(ID_CLIENTE);
        Parqueadero parqueadero = new Parqueadero();
        parqueadero.setId("parq-1");
        parqueadero.setTarifaHora(5_000d);

        reserva = new Reserva();
        reserva.setId(ID_RESERVA);
        reserva.setCliente(cliente);
        reserva.setParqueadero(parqueadero);
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reserva.setAnticipo(5_000d);
        reserva.setCreadaEn(LocalDateTime.now());
        reserva.setFechaReserva(LocalDateTime.now().plusHours(3));

        pago = new Pago();
        pago.setId("pago-1");
        pago.setIdReserva(ID_RESERVA);
        pago.setReferencia(REFERENCIA);
        pago.setMontoCentavos(CENTAVOS);
        pago.setEstado(EstadoPago.PENDIENTE);

        when(reservaRepository.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReferencia(REFERENCIA)).thenReturn(Optional.of(pago));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));
        // Por defecto los cambios de estado atómicos tienen éxito.
        when(pagoRepository.cambiarEstadoSi(anyString(), any(), any())).thenReturn(1L);
        when(reservaRepository.cambiarEstadoSi(anyString(), any(), any())).thenReturn(1L);
    }

    private void wompiDice(String estado, long centavos) {
        when(pasarela.consultarTransaccion("txn-1")).thenReturn(Optional.of(
                new Transaccion("txn-1", REFERENCIA, estado, centavos, "COP", "CARD")));
    }

    @Nested
    @DisplayName("Cuánto se cobra")
    class Monto {

        @Test
        @DisplayName("el anticipo es la tarifa de la primera hora")
        void primeraHora() {
            assertThat(servicio.calcularAnticipo(reserva.getParqueadero())).isEqualTo(5_000d);
        }

        @Test
        @DisplayName("nunca baja del mínimo que acepta la pasarela")
        void respetaElMinimo() {
            reserva.getParqueadero().setTarifaHora(800d);
            assertThat(servicio.calcularAnticipo(reserva.getParqueadero())).isEqualTo(1_500d);
        }

        @Test
        @DisplayName("sin llaves de la pasarela, los pagos están apagados")
        void apagadosSinLlaves() {
            properties.getPagos().getWompi().setLlavePublica("");
            assertThat(servicio.pagosHabilitados()).isFalse();
        }

        @Test
        @DisplayName("los pesos se convierten a centavos sin errores de redondeo")
        void centavos() {
            assertThat(PagoServiceImpl.aCentavos(5_000d)).isEqualTo(500_000);
            assertThat(PagoServiceImpl.aCentavos(4_999.99)).isEqualTo(499_999);
        }
    }

    @Nested
    @DisplayName("Iniciar el pago")
    class Iniciar {

        @Test
        @DisplayName("crea un intento con referencia propia y devuelve la URL de la pasarela")
        void creaElIntento() {
            when(pasarela.urlDePago(any(), anyString())).thenReturn("https://checkout.wompi.co/p/?x=1");

            String url = servicio.iniciarPago(ID_RESERVA, ID_CLIENTE);

            ArgumentCaptor<Pago> guardado = ArgumentCaptor.forClass(Pago.class);
            verify(pagoRepository).save(guardado.capture());
            assertThat(url).isEqualTo("https://checkout.wompi.co/p/?x=1");
            assertThat(guardado.getValue().getMontoCentavos()).isEqualTo(CENTAVOS);
            assertThat(guardado.getValue().getReferencia()).startsWith("PKG-AB34CD-");
            assertThat(guardado.getValue().getEstado()).isEqualTo(EstadoPago.PENDIENTE);
            // El retorno sale de la configuración, no de lo que mande el navegador.
            verify(pasarela).urlDePago(any(), org.mockito.ArgumentMatchers.eq("https://parking.test/pagos/wompi/retorno"));
        }

        @Test
        @DisplayName("dos intentos de la misma reserva nunca comparten referencia")
        void referenciasUnicas() {
            when(pasarela.urlDePago(any(), anyString())).thenReturn("url");
            servicio.iniciarPago(ID_RESERVA, ID_CLIENTE);
            servicio.iniciarPago(ID_RESERVA, ID_CLIENTE);

            ArgumentCaptor<Pago> guardados = ArgumentCaptor.forClass(Pago.class);
            verify(pagoRepository, org.mockito.Mockito.times(2)).save(guardados.capture());
            assertThat(guardados.getAllValues().get(0).getReferencia())
                    .isNotEqualTo(guardados.getAllValues().get(1).getReferencia());
        }

        @Test
        @DisplayName("la reserva de otro cliente responde igual que una inexistente")
        void reservaAjena() {
            assertThatThrownBy(() -> servicio.iniciarPago(ID_RESERVA, "otro-cliente"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            verify(pagoRepository, never()).save(any());
        }

        @Test
        @DisplayName("no se paga una reserva que ya no espera pago")
        void yaPagada() {
            reserva.setEstado(EstadoReserva.PENDIENTE);
            assertThatThrownBy(() -> servicio.iniciarPago(ID_RESERVA, ID_CLIENTE))
                    .isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("pasado el plazo ya no se puede pagar")
        void plazoVencido() {
            reserva.setCreadaEn(LocalDateTime.now().minusMinutes(16));
            assertThatThrownBy(() -> servicio.iniciarPago(ID_RESERVA, ID_CLIENTE))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("plazo");
        }
    }

    @Nested
    @DisplayName("Confirmar con la pasarela")
    class Confirmar {

        @Test
        @DisplayName("un pago aprobado deja la reserva lista para el parqueadero")
        void aprobado() {
            wompiDice("APPROVED", CENTAVOS);

            assertThat(servicio.confirmarTransaccion("txn-1")).isEqualTo(Resultado.APROBADO);

            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.PENDIENTE, EstadoPago.APROBADO);
            verify(reservaRepository).cambiarEstadoSi(ID_RESERVA, EstadoReserva.PENDIENTE_PAGO, EstadoReserva.PENDIENTE);
            assertThat(pago.getIdTransaccion()).isEqualTo("txn-1");
            assertThat(pago.getMetodo()).isEqualTo("CARD");
        }

        @Test
        @DisplayName("un pago rechazado no mueve la reserva y se puede reintentar")
        void rechazado() {
            wompiDice("DECLINED", CENTAVOS);

            assertThat(servicio.confirmarTransaccion("txn-1")).isEqualTo(Resultado.RECHAZADO);

            verify(reservaRepository, never()).cambiarEstadoSi(any(), any(), any());
        }

        @Test
        @DisplayName("aprobado por menos dinero del debido no confirma nada")
        void montoDistinto() {
            wompiDice("APPROVED", 100);

            assertThat(servicio.confirmarTransaccion("txn-1")).isEqualTo(Resultado.RECHAZADO);

            verify(pagoRepository, never()).cambiarEstadoSi("pago-1", EstadoPago.PENDIENTE, EstadoPago.APROBADO);
            verify(reservaRepository, never()).cambiarEstadoSi(any(), any(), any());
        }

        @Test
        @DisplayName("mientras la pasarela lo procesa, nada cambia")
        void enProceso() {
            wompiDice("PENDING", CENTAVOS);

            assertThat(servicio.confirmarTransaccion("txn-1")).isEqualTo(Resultado.EN_PROCESO);

            verify(pagoRepository, never()).cambiarEstadoSi(any(), any(), any());
        }

        @Test
        @DisplayName("confirmar dos veces el mismo pago no lo aplica dos veces")
        void idempotente() {
            wompiDice("APPROVED", CENTAVOS);
            pago.setEstado(EstadoPago.APROBADO);

            assertThat(servicio.confirmarTransaccion("txn-1")).isEqualTo(Resultado.APROBADO);

            verify(pagoRepository, never()).cambiarEstadoSi(any(), any(), any());
            verify(reservaRepository, never()).cambiarEstadoSi(any(), any(), any());
        }

        @Test
        @DisplayName("una transacción que la pasarela no conoce no confirma nada")
        void desconocida() {
            when(pasarela.consultarTransaccion("inventada")).thenReturn(Optional.empty());

            assertThat(servicio.confirmarTransaccion("inventada")).isEqualTo(Resultado.NO_ENCONTRADO);
        }

        @Test
        @DisplayName("una referencia que no es nuestra no confirma nada")
        void referenciaAjena() {
            when(pasarela.consultarTransaccion("txn-9")).thenReturn(Optional.of(
                    new Transaccion("txn-9", "OTRA-TIENDA-1", "APPROVED", CENTAVOS, "COP", "CARD")));
            when(pagoRepository.findByReferencia("OTRA-TIENDA-1")).thenReturn(Optional.empty());

            assertThat(servicio.confirmarTransaccion("txn-9")).isEqualTo(Resultado.NO_ENCONTRADO);
        }

        @Test
        @DisplayName("si se pagó cuando la reserva ya se había cancelado, el dinero se devuelve")
        void reservaYaCerrada() {
            wompiDice("APPROVED", CENTAVOS);
            when(reservaRepository.cambiarEstadoSi(ID_RESERVA, EstadoReserva.PENDIENTE_PAGO, EstadoReserva.PENDIENTE))
                    .thenReturn(0L);
            when(pasarela.devolver("txn-1")).thenReturn(true);

            assertThat(servicio.confirmarTransaccion("txn-1")).isEqualTo(Resultado.APROBADO_SIN_RESERVA);

            verify(pasarela).devolver("txn-1");
            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO);
        }
    }

    @Nested
    @DisplayName("Avisos de la pasarela")
    class Avisos {

        private final Map<String, Object> aviso =
                Map.of("data", Map.of("transaction", Map.of("id", "txn-1", "status", "APPROVED")));

        @Test
        @DisplayName("un aviso sin la firma de la pasarela se descarta")
        void sinFirma() {
            when(pasarela.avisoEsAutentico(aviso)).thenReturn(false);

            servicio.procesarAviso(aviso);

            verify(pasarela, never()).consultarTransaccion(anyString());
            verify(pagoRepository, never()).cambiarEstadoSi(any(), any(), any());
        }

        @Test
        @DisplayName("aunque el aviso diga APROBADO, manda lo que responda la API de la pasarela")
        void mandaLaApi() {
            when(pasarela.avisoEsAutentico(aviso)).thenReturn(true);
            wompiDice("DECLINED", CENTAVOS);

            servicio.procesarAviso(aviso);

            verify(pagoRepository, never()).cambiarEstadoSi("pago-1", EstadoPago.PENDIENTE, EstadoPago.APROBADO);
            verify(reservaRepository, never()).cambiarEstadoSi(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Devolver o retener")
    class DevolverORetener {

        @BeforeEach
        void conPagoAprobado() {
            pago.setEstado(EstadoPago.APROBADO);
            pago.setIdTransaccion("txn-1");
            when(pagoRepository.findFirstByIdReservaAndEstado(ID_RESERVA, EstadoPago.APROBADO))
                    .thenReturn(Optional.of(pago));
        }

        @Test
        @DisplayName("si la pasarela anula el cobro, el anticipo queda DEVUELTO")
        void devueltoPorLaPasarela() {
            when(pasarela.devolver("txn-1")).thenReturn(true);

            servicio.devolverAnticipo(reserva);

            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.APROBADO, EstadoPago.DEVOLUCION_PENDIENTE);
            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO);
        }

        @Test
        @DisplayName("si la pasarela no puede, queda pendiente de devolución manual y no se pierde")
        void devolucionManual() {
            when(pasarela.devolver("txn-1")).thenReturn(false);

            servicio.devolverAnticipo(reserva);

            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.APROBADO, EstadoPago.DEVOLUCION_PENDIENTE);
            verify(pagoRepository, never()).cambiarEstadoSi("pago-1", EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO);
        }

        @Test
        @DisplayName("dos devoluciones a la vez no anulan el cobro dos veces")
        void sinDobleDevolucion() {
            when(pagoRepository.cambiarEstadoSi("pago-1", EstadoPago.APROBADO, EstadoPago.DEVOLUCION_PENDIENTE))
                    .thenReturn(0L);

            servicio.devolverAnticipo(reserva);

            verify(pasarela, never()).devolver(anyString());
        }

        @Test
        @DisplayName("retener deja el anticipo al parqueadero sin tocar la pasarela")
        void retener() {
            servicio.retenerAnticipo(reserva);

            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.APROBADO, EstadoPago.RETENIDO);
            verify(pasarela, never()).devolver(anyString());
        }

        @Test
        @DisplayName("una reserva sin pago aprobado no mueve dinero")
        void sinPagoNoHaceNada() {
            when(pagoRepository.findFirstByIdReservaAndEstado(ID_RESERVA, EstadoPago.APROBADO))
                    .thenReturn(Optional.empty());

            servicio.devolverAnticipo(reserva);
            servicio.retenerAnticipo(reserva);

            verify(pagoRepository, never()).cambiarEstadoSi(any(), any(), any());
            verify(pasarela, never()).devolver(anyString());
        }

        @Test
        @DisplayName("ya aceptada: cancelar con más de una hora de antelación da derecho a devolución; con menos, no")
        void ventanaDeDevolucion() {
            reserva.setEstado(EstadoReserva.ACEPTADA);
            reserva.setFechaReserva(LocalDateTime.now().plusMinutes(90));
            assertThat(servicio.cancelarDaDerechoADevolucion(reserva)).isTrue();

            reserva.setFechaReserva(LocalDateTime.now().plusMinutes(30));
            assertThat(servicio.cancelarDaDerechoADevolucion(reserva)).isFalse();

            reserva.setFechaReserva(LocalDateTime.now().minusMinutes(5));
            assertThat(servicio.cancelarDaDerechoADevolucion(reserva)).isFalse();
        }

        @Test
        @DisplayName("sin aceptar: cancelar siempre devuelve, aunque falten pocos minutos para la llegada")
        void sinAceptarSiempreDevuelve() {
            reserva.setEstado(EstadoReserva.PENDIENTE);
            reserva.setFechaReserva(LocalDateTime.now().plusMinutes(10));
            assertThat(servicio.cancelarDaDerechoADevolucion(reserva)).isTrue();

            reserva.setFechaReserva(LocalDateTime.now().minusMinutes(5));
            assertThat(servicio.cancelarDaDerechoADevolucion(reserva)).isTrue();
        }
    }

    @Nested
    @DisplayName("Devoluciones manuales")
    class DevolucionesManuales {

        @Test
        @DisplayName("lista cada devolución pendiente con su cliente y su parqueadero")
        void listaConClienteYParqueadero() {
            pago.setEstado(EstadoPago.DEVOLUCION_PENDIENTE);
            pago.setIdCliente(ID_CLIENTE);
            pago.setIdParqueadero("parq-1");
            when(pagoRepository.findByEstado(any(), any())).thenReturn(List.of(pago));
            when(usuarioRepository.findAllById(List.of(ID_CLIENTE))).thenReturn(List.of(reserva.getCliente()));
            when(parqueaderoRepository.findAllById(List.of("parq-1"))).thenReturn(List.of(reserva.getParqueadero()));

            List<DevolucionPendiente> devoluciones = servicio.listarDevolucionesPendientes();

            assertThat(devoluciones).hasSize(1);
            assertThat(devoluciones.get(0).pago()).isSameAs(pago);
            assertThat(devoluciones.get(0).cliente()).isSameAs(reserva.getCliente());
            assertThat(devoluciones.get(0).parqueadero()).isSameAs(reserva.getParqueadero());
        }

        @Test
        @DisplayName("si el cliente se eliminó, la devolución igual aparece, sin sus datos")
        void clienteEliminado() {
            pago.setEstado(EstadoPago.DEVOLUCION_PENDIENTE);
            pago.setIdCliente("ya-no-existe");
            when(pagoRepository.findByEstado(any(), any())).thenReturn(List.of(pago));
            when(usuarioRepository.findAllById(any())).thenReturn(List.of());
            when(parqueaderoRepository.findAllById(any())).thenReturn(List.of());

            List<DevolucionPendiente> devoluciones = servicio.listarDevolucionesPendientes();

            assertThat(devoluciones).hasSize(1);
            assertThat(devoluciones.get(0).cliente()).isNull();
        }

        @Test
        @DisplayName("marcar como devuelto pasa el pago de DEVOLUCION_PENDIENTE a DEVUELTO")
        void marcarDevuelto() {
            when(pagoRepository.findById("pago-1")).thenReturn(Optional.of(pago));

            servicio.marcarDevuelto("pago-1", "super-1");

            verify(pagoRepository).cambiarEstadoSi("pago-1", EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO);
            assertThat(pago.getActualizadoEn()).isNotNull();
        }

        @Test
        @DisplayName("no se puede marcar dos veces, ni un pago que no estaba pendiente de devolución")
        void noSeMarcaDosVeces() {
            when(pagoRepository.cambiarEstadoSi("pago-1", EstadoPago.DEVOLUCION_PENDIENTE, EstadoPago.DEVUELTO))
                    .thenReturn(0L);

            assertThatThrownBy(() -> servicio.marcarDevuelto("pago-1", "super-1"))
                    .isInstanceOf(ReglaNegocioException.class);
            verify(pagoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Pagos cuyo aviso nunca llegó")
    class PagosPerdidos {

        @Test
        @DisplayName("si la pasarela dice que sí se pagó, la reserva se recupera")
        void recuperado() {
            when(pagoRepository.findByIdReserva(ID_RESERVA)).thenReturn(List.of(pago));
            when(pasarela.consultarPorReferencia(REFERENCIA)).thenReturn(Optional.of(
                    new Transaccion("txn-1", REFERENCIA, "APPROVED", CENTAVOS, "COP", "NEQUI")));

            assertThat(servicio.recuperarPagoPerdido(reserva)).isTrue();
            verify(reservaRepository).cambiarEstadoSi(ID_RESERVA, EstadoReserva.PENDIENTE_PAGO, EstadoReserva.PENDIENTE);
        }

        @Test
        @DisplayName("si no hay ningún pago aprobado, no se recupera nada")
        void nadaQueRecuperar() {
            when(pagoRepository.findByIdReserva(ID_RESERVA)).thenReturn(List.of(pago));
            when(pasarela.consultarPorReferencia(REFERENCIA)).thenReturn(Optional.empty());

            assertThat(servicio.recuperarPagoPerdido(reserva)).isFalse();
        }
    }
}
