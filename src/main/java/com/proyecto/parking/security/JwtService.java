package com.proyecto.parking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import java.time.Duration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {

    /** Nombre de la cookie donde viaja el JWT de la app web (login por sesion sustituido por JWT). */
    public static final String NOMBRE_COOKIE = "jwt";

    /** Debe coincidir con el valor por defecto de jwt.secret en application.yml. */
    private static final String SECRETO_DEV_POR_DEFECTO = "dev-only-secret-cambiar-en-produccion-32chars";

    private final SecretKey key;
    private final long expirationMs;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-ms}") long expirationMs,
                       @Value("${spring.profiles.active:}") String activeProfiles) {
        boolean esProduccion = Arrays.asList(activeProfiles.split(",")).contains("prod");
        if (esProduccion && SECRETO_DEV_POR_DEFECTO.equals(secret)) {
            throw new IllegalStateException(
                    "JWT_SECRET no esta definido. La app no puede arrancar en produccion con el secreto de desarrollo.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Constructor de conveniencia para pruebas unitarias (sin perfil activo). */
    public JwtService(String secret, long expirationMs) {
        this(secret, expirationMs, "");
    }

    public String generarToken(String correo, String rol) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);

        return Jwts.builder()
                .subject(correo)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(key)
                .compact();
    }

    public Claims validarYObtenerClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Cookie con el JWT de la app web: HttpOnly (JS no la puede leer), con la
     * misma vida que el token, y SameSite=Lax (el navegador no la manda en
     * peticiones cross-site que muten estado, mitigando CSRF).
     */
    public ResponseCookie crearCookieJwt(String token, boolean segura) {
        return ResponseCookie.from(NOMBRE_COOKIE, token)
                .httpOnly(true)
                .secure(segura)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofMillis(expirationMs))
                .build();
    }

    /** Cookie que borra la del JWT (Max-Age 0), para el logout. */
    public ResponseCookie crearCookieJwtVacia(boolean segura) {
        return ResponseCookie.from(NOMBRE_COOKIE, "")
                .httpOnly(true)
                .secure(segura)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }
}
