package com.proyecto.parking.security;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Cierre del login con Google: traduce la identidad que entrega Google a un
 * {@link Usuario} de Mongo y le entrega la misma cookie JWT que el login por
 * formulario (ver {@link LoginSuccessHandler#entregarCookieYRedirigir}).
 *
 * <p>Lo que llega aquí no es un {@link UsuarioPrincipal} sino un
 * {@link OAuth2User}: los datos que Google dice del usuario. El rol, el estado
 * y todo lo demás salen de la base de datos, nunca de Google.</p>
 */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginSuccessHandler.class);

    private final UsuarioService usuarioService;
    private final LoginSuccessHandler loginSuccessHandler;

    public GoogleLoginSuccessHandler(UsuarioService usuarioService, LoginSuccessHandler loginSuccessHandler) {
        this.usuarioService = usuarioService;
        this.loginSuccessHandler = loginSuccessHandler;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        descartarSesionTemporal(request);
        String base = request.getContextPath();

        OAuth2User datosGoogle = (OAuth2User) authentication.getPrincipal();
        String correo = datosGoogle.getAttribute("email");
        // Declarado como Object a propósito: getAttribute es genérico, y pasado
        // directo a String.valueOf el compilador elige valueOf(char[]) y el
        // Boolean de Google revienta con ClassCastException.
        Object correoVerificado = datosGoogle.getAttribute("email_verified");

        // Sin correo verificado no se vincula: alguien podría crear una cuenta de
        // Google con el correo de otro usuario y, sin esta comprobación, entrar a
        // la cuenta de ese usuario en el sistema.
        if (correo == null || !Boolean.parseBoolean(String.valueOf(correoVerificado))) {
            log.warn("Login con Google rechazado: correo ausente o no verificado ({}).", correo);
            response.sendRedirect(base + "/login?error=google");
            return;
        }

        Usuario usuario = usuarioService.obtenerOCrearUsuarioGoogle(correo, datosGoogle.getAttribute("name"));

        if (!usuario.isHabilitado() || usuario.getRol() == null) {
            log.info("Login con Google de {} rechazado: cuenta deshabilitada.", usuario.getCorreo());
            response.sendRedirect(base + "/login?error=deshabilitado");
            return;
        }

        UsuarioPrincipal principal = new UsuarioPrincipal(usuario);
        log.info("Inicio de sesión con Google de {} (rol {}).", principal.getCorreo(), principal.getRol());

        loginSuccessHandler.entregarCookieYRedirigir(request, response, principal);
    }

    /**
     * La cadena web es STATELESS, pero Spring guarda en una sesión el
     * {@code state} del viaje a Google (protección CSRF del propio flujo
     * OAuth2) para compararlo a la vuelta. Terminado el login ya no sirve: se
     * destruye para que la identidad viaje solo en la cookie JWT.
     */
    static void descartarSesionTemporal(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }
}
