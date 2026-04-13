package com.proyecto.parking.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String ip = request.getRemoteAddr();

        if (loginAttemptService.isBlocked(ip)) {
            response.sendRedirect("/login?error=bloqueado");
            return;
        }

        loginAttemptService.loginFallido(ip);

        if (loginAttemptService.isBlocked(ip)) {
            response.sendRedirect("/login?error=bloqueado");
            return;
        }

        String errorParam;
        if (exception instanceof DisabledException) {
            errorParam = "deshabilitado";
        } else {
            String mensaje = exception.getMessage();
            if (mensaje != null && mensaje.contains("parqueadero")) {
                errorParam = "parqueadero";
            } else {
                errorParam = "credenciales";
            }
        }

        response.sendRedirect("/login?error=" + errorParam);
    }
}
