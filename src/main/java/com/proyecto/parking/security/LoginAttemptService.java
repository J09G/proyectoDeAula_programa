package com.proyecto.parking.security;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_INTENTOS = 5;
    private static final int MINUTOS_BLOQUEO = 30;

    // IP → número de intentos fallidos
    private final ConcurrentHashMap<String, Integer> intentos = new ConcurrentHashMap<>();

    // IP → momento en que expira el bloqueo
    private final ConcurrentHashMap<String, LocalDateTime> bloqueados = new ConcurrentHashMap<>();

    public void loginFallido(String ip) {
        if (isBlocked(ip)) return;

        int intentosActuales = intentos.getOrDefault(ip, 0) + 1;
        intentos.put(ip, intentosActuales);

        if (intentosActuales >= MAX_INTENTOS) {
            bloqueados.put(ip, LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
            intentos.remove(ip);
        }
    }

    public void loginExitoso(String ip) {
        intentos.remove(ip);
        bloqueados.remove(ip);
    }

    public boolean isBlocked(String ip) {
        LocalDateTime expiracion = bloqueados.get(ip);
        if (expiracion == null) return false;

        if (LocalDateTime.now().isAfter(expiracion)) {
            bloqueados.remove(ip);
            return false;
        }
        return true;
    }
}
