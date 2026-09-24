package com.proyecto.parking.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Contabiliza el intento fallido y devuelve al login con un código de error.
 *
 * <p>Los códigos son deliberadamente vagos para no revelar si un correo existe
 * en el sistema: un atacante no debe poder distinguir "no existe" de
 * "contraseña incorrecta".</p>
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginFailureHandler.class);

    private final LoginAttemptService loginAttemptService;

    public LoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String ip = IpUtils.obtenerIp(request);
        String base = request.getContextPath();

        if (loginAttemptService.estaBloqueada(ip)) {
            response.sendRedirect(base + "/login?error=bloqueado");
            return;
        }

        loginAttemptService.loginFallido(ip);

        if (loginAttemptService.estaBloqueada(ip)) {
            response.sendRedirect(base + "/login?error=bloqueado");
            return;
        }

        log.debug("Login fallido desde {}: {}", ip, exception.getClass().getSimpleName());

        String codigo = (exception instanceof DisabledException) ? "deshabilitado" : "credenciales";
        response.sendRedirect(base + "/login?error=" + codigo);
    }
}
