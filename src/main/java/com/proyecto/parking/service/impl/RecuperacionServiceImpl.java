package com.proyecto.parking.service.impl;

import com.proyecto.parking.config.ParkingProperties;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.TokenRecuperacion;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.TokenRecuperacionRepository;
import com.proyecto.parking.repository.UsuarioRepository;
import com.proyecto.parking.service.EmailService;
import com.proyecto.parking.service.RecuperacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class RecuperacionServiceImpl implements RecuperacionService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionServiceImpl.class);

    public static final String ENLACE_INVALIDO = "El enlace no es válido o ya venció. Pide uno nuevo.";

    /** Generador criptográfico: un Random normal es predecible y sus tokens se podrían adivinar. */
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final ParkingProperties properties;

    public RecuperacionServiceImpl(UsuarioRepository usuarioRepository,
                                   TokenRecuperacionRepository tokenRepository,
                                   PasswordEncoder passwordEncoder,
                                   EmailService emailService,
                                   ParkingProperties properties) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.properties = properties;
    }

    @Override
    public void solicitarRecuperacion(String correo) {
        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> encontrado = usuarioRepository.findByCorreo(correoNormalizado);

        // Sin excepción ni mensaje distinto: el controlador responde igual en
        // todos los casos. Y como el correo sale en segundo plano (@Async), la
        // respuesta tampoco tarda más cuando el usuario sí existe.
        if (encontrado.isEmpty() || !encontrado.get().isHabilitado()) {
            log.info("Recuperación pedida para un correo inexistente o deshabilitado.");
            return;
        }
        Usuario usuario = encontrado.get();
        Instant ahora = Instant.now();
        ParkingProperties.Recuperacion config = properties.getRecuperacion();

        // Límite de envío: evita usar la app para llenar de correos a alguien.
        if (tokenRepository.existsByIdUsuarioAndCreadoEnAfter(
                usuario.getId(), ahora.minusSeconds(config.getSegundosEntreEnvios()))) {
            log.info("Recuperación de {} ignorada: ya se envió un enlace hace menos de {} s.",
                    usuario.getId(), config.getSegundosEntreEnvios());
            return;
        }

        // Solo vale el último enlace pedido.
        tokenRepository.deleteByIdUsuario(usuario.getId());

        String token = generarToken();
        tokenRepository.save(new TokenRecuperacion(huella(token), usuario.getId(), ahora,
                ahora.plus(Duration.ofMinutes(config.getMinutosVigencia()))));

        String enlace = UriComponentsBuilder.fromUriString(properties.getUrlPublica())
                .path("/restablecer")
                .queryParam("token", token)
                .build()
                .toUriString();

        emailService.enviarCorreo(usuario.getCorreo(), "Restablece tu contraseña de ParkingApp", """
                Hola %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta.
                Para elegir una nueva, abre este enlace (vence en %d minutos y sirve una sola vez):

                %s

                Si no fuiste tú, ignora este correo: tu contraseña no cambiará.
                """.formatted(usuario.getNombre(), config.getMinutosVigencia(), enlace));

        log.info("Enlace de recuperación enviado al usuario {}.", usuario.getId());
    }

    @Override
    public boolean enlaceValido(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenRepository.findByHashToken(huella(token))
                .filter(t -> !t.estaVencido(Instant.now()))
                .isPresent();
    }

    @Override
    public void restablecerContrasena(String token, String contrasenaNueva) {
        // Buscar y borrar en una sola operación: el enlace sirve una vez aunque
        // se envíe dos veces al mismo tiempo.
        TokenRecuperacion usado = (token == null || token.isBlank())
                ? null
                : tokenRepository.deleteByHashToken(huella(token));

        // El TTL de Mongo puede tardar hasta ~1 min en borrar uno vencido.
        if (usado == null || usado.estaVencido(Instant.now())) {
            throw new ReglaNegocioException(ENLACE_INVALIDO);
        }

        Usuario usuario = usuarioRepository.findById(usado.getIdUsuario())
                .orElseThrow(() -> new ReglaNegocioException(ENLACE_INVALIDO));
        usuario.setContrasena(passwordEncoder.encode(contrasenaNueva));
        usuarioRepository.save(usuario);

        // Cualquier otro enlace pendiente de este usuario deja de servir.
        tokenRepository.deleteByIdUsuario(usuario.getId());
        log.info("Contraseña restablecida por enlace para el usuario {}.", usuario.getId());
    }

    /** 32 bytes aleatorios (256 bits) en Base64 apto para URL. */
    private static String generarToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash SHA-256 en hexadecimal. Basta un hash rápido (no BCrypt) porque el
     * token es aleatorio de 256 bits, imposible de adivinar; y debe ser sin sal
     * para poder buscar el token por su hash.
     */
    public static String huella(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM está obligada a incluir SHA-256.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
