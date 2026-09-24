package com.proyecto.parking.service.impl;

import com.proyecto.parking.exception.AccesoDenegadoException;
import com.proyecto.parking.exception.RecursoNoEncontradoException;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.ParqueaderoRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.repository.ZonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParqueaderoServiceImplTest {

    private static final String ID_PARQUEADERO = "parq-1";
    private static final String ID_DUENO = "admin-dueno";
    private static final String ID_INTRUSO = "admin-intruso";

    @Mock private ParqueaderoRepository parqueaderoRepository;
    @Mock private ZonaRepository zonaRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @InjectMocks private ParqueaderoServiceImpl servicio;

    private Parqueadero parqueadero;

    @BeforeEach
    void prepararParqueadero() {
        Usuario dueno = new Usuario();
        dueno.setId(ID_DUENO);

        parqueadero = new Parqueadero();
        parqueadero.setId(ID_PARQUEADERO);
        parqueadero.setEspaciosTotales(10);
        parqueadero.setEspaciosDisponibles(4);
        parqueadero.setAdministrador(dueno);
    }

    @Nested
    @DisplayName("Comprobación de propiedad")
    class Propiedad {

        @Test
        @DisplayName("el dueño accede a su parqueadero")
        void elDuenoAccede() {
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            Parqueadero resultado = servicio.obtenerParqueaderoDeAdministrador(ID_PARQUEADERO, ID_DUENO);

            assertThat(resultado.getId()).isEqualTo(ID_PARQUEADERO);
        }

        @Test
        @DisplayName("otro administrador recibe 403 aunque acierte el id de la URL")
        void otroAdministradorEsRechazado() {
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            assertThatThrownBy(() -> servicio.obtenerParqueaderoDeAdministrador(ID_PARQUEADERO, ID_INTRUSO))
                    .isInstanceOf(AccesoDenegadoException.class)
                    .hasMessageContaining("no te pertenece");
        }

        @Test
        @DisplayName("un parqueadero sin administrador no pertenece a nadie")
        void sinAdministradorNadieAccede() {
            parqueadero.setAdministrador(null);
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            assertThatThrownBy(() -> servicio.obtenerParqueaderoDeAdministrador(ID_PARQUEADERO, ID_DUENO))
                    .isInstanceOf(AccesoDenegadoException.class);
        }

        @Test
        @DisplayName("un id inexistente da 404, no 403")
        void idInexistente() {
            when(parqueaderoRepository.findById("no-existe")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.obtenerParqueaderoDeAdministrador("no-existe", ID_DUENO))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    @Nested
    @DisplayName("Ocupación de cupos")
    class Cupos {

        @Test
        @DisplayName("ocupar un espacio usa el decremento atómico")
        void ocuparUsaDecrementoAtomico() {
            when(parqueaderoRepository.reservarEspaciosSiHayDisponibles(ID_PARQUEADERO, 1, -1)).thenReturn(1L);

            assertThatCode(() -> servicio.ocuparEspacio(ID_PARQUEADERO)).doesNotThrowAnyException();

            verify(parqueaderoRepository).reservarEspaciosSiHayDisponibles(ID_PARQUEADERO, 1, -1);
            verify(parqueaderoRepository, never()).save(parqueadero);
        }

        @Test
        @DisplayName("si el parqueadero está lleno, la operación falla en vez de dejar el contador en negativo")
        void parqueaderoLleno() {
            when(parqueaderoRepository.reservarEspaciosSiHayDisponibles(ID_PARQUEADERO, 1, -1)).thenReturn(0L);

            assertThatThrownBy(() -> servicio.ocuparEspacio(ID_PARQUEADERO))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("No hay espacios disponibles");
        }

        @Test
        @DisplayName("liberar devuelve el cupo con un incremento atómico")
        void liberarIncrementa() {
            servicio.liberarEspacio(ID_PARQUEADERO);

            verify(parqueaderoRepository).ajustarEspaciosDisponibles(ID_PARQUEADERO, 1);
        }
    }

    @Nested
    @DisplayName("Cambio de capacidad")
    class Capacidad {

        @Test
        @DisplayName("no permite dejar disponibles por encima de los totales")
        void disponiblesNoSuperanTotales() {
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            assertThatThrownBy(() -> servicio.actualizarCapacidad(ID_PARQUEADERO, ID_DUENO, 10, 12))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no pueden superar");
        }

        @Test
        @DisplayName("no permite reducir el total por debajo de los coches que ya están dentro")
        void noReduceDebajoDeLoOcupado() {
            // 10 totales - 4 disponibles = 6 ocupados ahora mismo.
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            assertThatThrownBy(() -> servicio.actualizarCapacidad(ID_PARQUEADERO, ID_DUENO, 5, 5))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("6 espacios ocupados");
        }

        @Test
        @DisplayName("un administrador ajeno no puede tocar la capacidad")
        void intrusoNoCambiaCapacidad() {
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            assertThatThrownBy(() -> servicio.actualizarCapacidad(ID_PARQUEADERO, ID_INTRUSO, 20, 20))
                    .isInstanceOf(AccesoDenegadoException.class);

            verify(parqueaderoRepository, never()).save(parqueadero);
        }

        @Test
        @DisplayName("una ampliación válida se guarda")
        void ampliacionValida() {
            when(parqueaderoRepository.findById(ID_PARQUEADERO)).thenReturn(Optional.of(parqueadero));

            servicio.actualizarCapacidad(ID_PARQUEADERO, ID_DUENO, 20, 14);

            assertThat(parqueadero.getEspaciosTotales()).isEqualTo(20);
            assertThat(parqueadero.getEspaciosDisponibles()).isEqualTo(14);
            verify(parqueaderoRepository).save(parqueadero);
        }
    }

    @Test
    @DisplayName("buscar por cédula de administrador sin resultados no consulta parqueaderos")
    void busquedaSinAdministradores() {
        when(usuarioRepository.findByCedulaContaining("000")).thenReturn(List.of());

        var resultado = servicio.buscarPorCedulaAdmin("000", PageRequest.of(0, 20));

        assertThat(resultado).isEmpty();
        verify(parqueaderoRepository, never()).findByAdministrador_IdIn(anyList(), any(Pageable.class));
    }
}
