package com.proyecto.parking.security;

import com.proyecto.parking.config.ParkingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloqueo temporal por IP tras varios intentos de login fallidos.
 *
 * <p>El estado vive en memoria: es suficiente para una sola instancia, pero con
 * varias réplicas cada una llevaría su propia cuenta. Para eso habría que
 * moverlo a Redis o a la propia base de datos.</p>
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Tope de IPs vigiladas, para que el mapa no crezca sin control. */
    private static final int MAX_ENTRADAS = 10_000;

    private final int maxIntentos;
    private final Duration duracionBloqueo;

    private final Map<String, Integer> intentos = new ConcurrentHashMap<>();
    private final Map<String, Instant> bloqueados = new ConcurrentHashMap<>();

    public LoginAttemptService(ParkingProperties properties) {
        this.maxIntentos = properties.getSeguridad().getMaxIntentosLogin();
        this.duracionBloqueo = Duration.ofMinutes(properties.getSeguridad().getMinutosBloqueo());
    }

    public void loginFallido(String ip) {
        if (estaBloqueada(ip)) {
            return;
        }
        purgarSiHaceFalta();

        int acumulados = intentos.merge(ip, 1, Integer::sum);
        if (acumulados >= maxIntentos) {
            bloqueados.put(ip, Instant.now().plus(duracionBloqueo));
            intentos.remove(ip);
            log.warn("IP {} bloqueada durante {} minutos tras {} intentos fallidos.",
                    ip, duracionBloqueo.toMinutes(), maxIntentos);
        }
    }

    public void loginExitoso(String ip) {
        intentos.remove(ip);
        bloqueados.remove(ip);
    }

    public boolean estaBloqueada(String ip) {
        Instant expiracion = bloqueados.get(ip);
        if (expiracion == null) {
            return false;
        }
        if (Instant.now().isAfter(expiracion)) {
            bloqueados.remove(ip);
            return false;
        }
        return true;
    }

    /** Elimina bloqueos ya vencidos cuando el mapa se acerca al tope. */
    private void purgarSiHaceFalta() {
        if (intentos.size() + bloqueados.size() < MAX_ENTRADAS) {
            return;
        }
        Instant ahora = Instant.now();
        bloqueados.entrySet().removeIf(e -> ahora.isAfter(e.getValue()));
        if (intentos.size() >= MAX_ENTRADAS) {
            intentos.clear();
        }
    }
}
