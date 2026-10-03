package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.TokenRecuperacion;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.TokenRecuperacionRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.EmailService;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecuperacionServiceImplTest {

    private static final String ID_USUARIO = "usr-1";
    private static final String CORREO = "ana@correo.com";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TokenRecuperacionRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private RecuperacionServiceImpl servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        ParkingProperties properties = new ParkingProperties();
        properties.setUrlPublica("https://parking.ejemplo.com");

        servicio = new RecuperacionServiceImpl(usuarioRepository, tokenRepository, passwordEncoder,
                emailService, properties);

        ana = new Usuario();
        ana.setId(ID_USUARIO);
        ana.setNombre("Ana");
        ana.setCorreo(CORREO);
        ana.setHabilitado(true);

        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(ana));
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(ana));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "{bcrypt}hash-de-" + inv.getArgument(0));
    }

    private TokenRecuperacion tokenQueVence(Instant expiraEn) {
        return new TokenRecuperacion("hash", ID_USUARIO, expiraEn.minus(Duration.ofMinutes(30)), expiraEn);
    }

    @Nested
    @DisplayName("Pedir el enlace")
    class Solicitar {

        @Test
        @DisplayName("guarda solo la huella del token y envía el token real en el enlace")
        void guardaHuellaYEnviaEnlace() {
            servicio.solicitarRecuperacion("  Ana@Correo.com ");

            ArgumentCaptor<TokenRecuperacion> guardado = ArgumentCaptor.forClass(TokenRecuperacion.class);
            verify(tokenRepository).save(guardado.capture());
            ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
            verify(emailService).enviarCorreo(eq(CORREO), anyString(), cuerpo.capture());

            Matcher enlace = Pattern.compile("(https://\\S+/restablecer\\?token=)(\\S+)").matcher(cuerpo.getValue());
            assertThat(enlace.find()).as("el correo debe traer el enlace").isTrue();
            String token = enlace.group(2);

            assertThat(enlace.group(1)).isEqualTo("https://parking.ejemplo.com/restablecer?token=");
            assertThat(guardado.getValue().getHashToken())
                    .as("en la BD va la huella, no el token")
                    .isNotEqualTo(token)
                    .isEqualTo(RecuperacionServiceImpl.huella(token));
            assertThat(Duration.between(guardado.getValue().getCreadoEn(), guardado.getValue().getExpiraEn()))
                    .isEqualTo(Duration.ofMinutes(30));
            verify(tokenRepository).deleteByIdUsuario(ID_USUARIO);
        }

        @Test
        @DisplayName("un correo inexistente no falla ni envía nada")
        void correoInexistente() {
            servicio.solicitarRecuperacion("nadie@correo.com");

            verify(tokenRepository, never()).save(any());
            verify(emailService, never()).enviarCorreo(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("una cuenta deshabilitada no recibe enlace")
        void cuentaDeshabilitada() {
            ana.setHabilitado(false);

            servicio.solicitarRecuperacion(CORREO);

            verify(emailService, never()).enviarCorreo(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("no envía otro correo si ya se envió uno hace menos de un minuto")
        void limiteDeEnvio() {
            when(tokenRepository.existsByIdUsuarioAndCreadoEnAfter(eq(ID_USUARIO), any())).thenReturn(true);

            servicio.solicitarRecuperacion(CORREO);

            verify(tokenRepository, never()).save(any());
            verify(emailService, never()).enviarCorreo(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("dos tokens seguidos son distintos (aleatorios)")
        void tokensDistintos() {
            servicio.solicitarRecuperacion(CORREO);
            servicio.solicitarRecuperacion(CORREO);

            ArgumentCaptor<TokenRecuperacion> guardados = ArgumentCaptor.forClass(TokenRecuperacion.class);
            verify(tokenRepository, org.mockito.Mockito.times(2)).save(guardados.capture());
            assertThat(guardados.getAllValues().get(0).getHashToken())
                    .isNotEqualTo(guardados.getAllValues().get(1).getHashToken());
        }
    }

    @Nested
    @DisplayName("Usar el enlace")
    class Restablecer {

        @Test
        @DisplayName("con un enlace vigente guarda la contraseña nueva cifrada y anula los demás enlaces")
        void enlaceVigente() {
            when(tokenRepository.deleteByHashToken(RecuperacionServiceImpl.huella("token-ok")))
                    .thenReturn(tokenQueVence(Instant.now().plus(Duration.ofMinutes(10))));

            servicio.restablecerContrasena("token-ok", "nuevaClave123");

            assertThat(ana.getContrasena()).isEqualTo("{bcrypt}hash-de-nuevaClave123");
            verify(usuarioRepository).save(ana);
            verify(tokenRepository).deleteByIdUsuario(ID_USUARIO);
        }

        @Test
        @DisplayName("un enlace vencido que Mongo aún no borró se rechaza")
        void enlaceVencido() {
            when(tokenRepository.deleteByHashToken(anyString()))
                    .thenReturn(tokenQueVence(Instant.now().minusSeconds(5)));

            assertThatThrownBy(() -> servicio.restablecerContrasena("token-viejo", "nuevaClave123"))
                    .isInstanceOf(ReglaNegocioException.class);
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("un enlace ya usado (o inventado) se rechaza")
        void enlaceUsado() {
            when(tokenRepository.deleteByHashToken(anyString())).thenReturn(null);

            assertThatThrownBy(() -> servicio.restablecerContrasena("token-usado", "nuevaClave123"))
                    .isInstanceOf(ReglaNegocioException.class);
            verify(usuarioRepository, never()).save(any());
        }
    }
}
