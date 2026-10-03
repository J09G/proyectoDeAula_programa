package com.proyecto.parking.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * El viaje a Google no terminó bien: el usuario canceló, el {@code state} no
 * coincide o Google rechazó las credenciales de la app.
 *
 * <p>No reutiliza {@link LoginFailureHandler} a propósito: ese cuenta intentos
 * fallidos por IP para frenar a quien adivina contraseñas, y cancelar en Google
 * no es eso.</p>
 */
@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginFailureHandler.class);

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        GoogleLoginSuccessHandler.descartarSesionTemporal(request);
        log.info("Login con Google fallido: {}", exception.getMessage());
        response.sendRedirect(request.getContextPath() + "/login?error=google");
    }
}
