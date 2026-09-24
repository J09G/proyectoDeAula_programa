package com.proyecto.parking.service.impl;

import com.proyecto.parking.dto.EntradaForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.RegistroParqueo.EstadoRegistro;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.RegistroParqueoRepository;
import com.proyecto.parking.repository.ReservaRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.ParqueaderoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistroParqueoServiceImplTest {

    private static final String ID_PARQUEADERO = "parq-1";
    private static final String ID_ADMIN = "admin-1";
    private static final String PLACA = "ABC123";

    @Mock private RegistroParqueoRepository registroRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ParqueaderoService parqueaderoService;

    @InjectMocks private RegistroParqueoServiceImpl servicio;

    private Parqueadero parqueadero;

    @BeforeEach
    void preparar() {
        parqueadero = new Parqueadero();
        parqueadero.setId(ID_PARQUEADERO);
        parqueadero.setEspaciosTotales(10);
        parqueadero.setEspaciosDisponibles(5);
        parqueadero.setTarifaHora(3000d);

        when(parqueaderoService.obtenerParqueaderoDeAdministrador(ID_PARQUEADERO, ID_ADMIN))
                .thenReturn(parqueadero);
        when(registroRepository.save(any(RegistroParqueo.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EntradaForm entrada(int espacio, String cedula) {
        EntradaForm form = new EntradaForm();
        form.setPlaca(PLACA);
        form.setEspacioReservado(espacio);
        form.setCedula(cedula);
        return form;
    }

    @Test
    @DisplayName("una entrada sin reserva toma un cupo y congela la tarifa vigente")
    void entradaSinReserva() {
        RegistroParqueo registro = servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, null));

        assertThat(registro.getEstado()).isEqualTo(EstadoRegistro.ACTIVO);
        assertThat(registro.getEspacioReservado()).isEqualTo(3);
        assertThat(registro.getTarifaHoraAplicada()).isEqualTo(3000d);
        verify(parqueaderoService).ocuparEspacio(ID_PARQUEADERO);
    }

    @Test
    @DisplayName("rechaza meter un coche en un cubículo ya ocupado")
    void cubiculoOcupado() {
        RegistroParqueo ocupante = new RegistroParqueo();
        ocupante.setPlaca("XYZ789");
        when(registroRepository.findByParqueadero_IdAndEspacioReservadoAndEstado(
                ID_PARQUEADERO, 3, EstadoRegistro.ACTIVO)).thenReturn(Optional.of(ocupante));

        assertThatThrownBy(() -> servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("XYZ789");

        verify(parqueaderoService, never()).ocuparEspacio(anyString());
    }

    @Test
    @DisplayName("rechaza una placa que ya está dentro sin salida registrada")
    void placaDuplicada() {
        when(registroRepository.findByPlacaAndEstado(PLACA, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(new RegistroParqueo()));

        assertThatThrownBy(() -> servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("entrada activa");
    }

    @Test
    @DisplayName("rechaza un cubículo que no existe")
    void cubiculoInexistente() {
        assertThatThrownBy(() -> servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(99, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("una entrada con reserva no vuelve a descontar cupo y marca la reserva UTILIZADA")
    void entradaConReserva() {
        Usuario cliente = new Usuario();
        cliente.setId("cli-1");
        cliente.setCedula("1023456789");

        Reserva reserva = new Reserva();
        reserva.setId("res-1");
        reserva.setEspacioReservado(3);
        reserva.setEstado(EstadoReserva.ACEPTADA);

        when(usuarioRepository.findByCedula("1023456789")).thenReturn(Optional.of(cliente));
        when(reservaRepository.findByCliente_IdAndParqueadero_IdAndEstado(
                "cli-1", ID_PARQUEADERO, EstadoReserva.ACEPTADA)).thenReturn(List.of(reserva));

        RegistroParqueo registro =
                servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, "1023456789"));

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.UTILIZADA);
        assertThat(registro.getReserva()).isSameAs(reserva);
        // El cupo ya se había descontado al aceptar la reserva.
        verify(parqueaderoService, never()).ocuparEspacio(anyString());
    }

    @Test
    @DisplayName("si el cliente entra por un cubículo distinto al reservado, se avisa")
    void cubiculoDistintoAlReservado() {
        Usuario cliente = new Usuario();
        cliente.setId("cli-1");

        Reserva reserva = new Reserva();
        reserva.setEspacioReservado(7);
        reserva.setEstado(EstadoReserva.ACEPTADA);

        when(usuarioRepository.findByCedula("1023456789")).thenReturn(Optional.of(cliente));
        when(reservaRepository.findByCliente_IdAndParqueadero_IdAndEstado(
                "cli-1", ID_PARQUEADERO, EstadoReserva.ACEPTADA)).thenReturn(List.of(reserva));

        assertThatThrownBy(() ->
                servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, "1023456789")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("tiene reservado el cubículo 7");
    }

    @Test
    @DisplayName("no se puede ocupar un cubículo reservado por otro cliente")
    void cubiculoReservadoPorOtro() {
        when(reservaRepository.findByParqueadero_IdAndEspacioReservadoAndEstado(
                ID_PARQUEADERO, 3, EstadoReserva.ACEPTADA)).thenReturn(List.of(new Reserva()));

        assertThatThrownBy(() -> servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("reservado para otro cliente");
    }

    @Test
    @DisplayName("una cédula desconocida no se acepta en silencio")
    void cedulaDesconocida() {
        when(usuarioRepository.findByCedula("9999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicio.registrarEntrada(ID_PARQUEADERO, ID_ADMIN, entrada(3, "9999999999")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("No hay ningún usuario");
    }

    @Test
    @DisplayName("la salida cobra con la tarifa congelada, no con la actual")
    void salidaUsaTarifaCongelada() {
        RegistroParqueo registro = new RegistroParqueo();
        registro.setId("reg-1");
        registro.setPlaca(PLACA);
        registro.setParqueadero(parqueadero);
        registro.setEstado(EstadoRegistro.ACTIVO);
        registro.setHoraEntrada(LocalDateTime.now().minusHours(2));
        registro.setTarifaHoraAplicada(3000d);

        // El administrador subió la tarifa mientras el coche estaba dentro.
        parqueadero.setTarifaHora(9000d);

        when(registroRepository.findById("reg-1")).thenReturn(Optional.of(registro));

        RegistroParqueo finalizado = servicio.registrarSalida("reg-1", ID_ADMIN);

        assertThat(finalizado.getEstado()).isEqualTo(EstadoRegistro.FINALIZADO);
        assertThat(finalizado.getValorPagado()).isEqualTo(6000.00);
        verify(parqueaderoService).liberarEspacio(ID_PARQUEADERO);
    }

    @Test
    @DisplayName("no se puede registrar dos veces la salida")
    void salidaDuplicada() {
        RegistroParqueo registro = new RegistroParqueo();
        registro.setParqueadero(parqueadero);
        registro.setEstado(EstadoRegistro.FINALIZADO);

        when(registroRepository.findById("reg-1")).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> servicio.registrarSalida("reg-1", ID_ADMIN))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("ya fue finalizado");

        verify(parqueaderoService, never()).liberarEspacio(anyString());
    }
}
