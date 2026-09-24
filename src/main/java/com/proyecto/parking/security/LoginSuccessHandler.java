package com.proyecto.parking.security;

import com.proyecto.parking.model.Rol;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Redirige a cada rol a su panel tras un login correcto. */
@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final LoginAttemptService loginAttemptService;

    public LoginSuccessHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        loginAttemptService.loginExitoso(IpUtils.obtenerIp(request));

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        log.info("Inicio de sesión de {} (rol {}).", principal.getCorreo(), principal.getRol());

        response.sendRedirect(request.getContextPath() + destinoPara(principal));
    }

    /** Panel inicial de cada rol. */
    public static String destinoPara(UsuarioPrincipal principal) {
        if (principal.tieneRol(Rol.CLIENTE)) {
            return "/cliente";
        }
        if (principal.tieneRol(Rol.ADMINISTRADOR)) {
            return "/admin";
        }
        if (principal.tieneRol(Rol.SUPERADMIN)) {
            return "/superadmin";
        }
        return "/login?error=credenciales";
    }
}
