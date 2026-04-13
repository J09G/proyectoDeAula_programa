package com.proyecto.parking.security;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String ip = request.getRemoteAddr();
        loginAttemptService.loginExitoso(ip);

        String correo = authentication.getName();
        Usuario usuario = usuarioService.obtenerUsuarioPorCorreo(correo);

        String rol = usuario.getRol().getNombre().toLowerCase();

        if (rol.equals("administrador")) {
            Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorAdministrador(usuario.getId());
            if (parqueadero == null || !parqueadero.getHabilitado()) {
                request.getSession().invalidate();
                request.getSession(true); // nueva sesión vacía para evitar redirección a ?timeout
                response.sendRedirect("/login?error=parqueadero");
                return;
            }
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("usuario", usuario);

        switch (rol) {
            case "cliente" -> response.sendRedirect("/cliente");
            case "administrador" -> response.sendRedirect("/admin");
            case "superadmin" -> response.sendRedirect("/superadmin");
            default -> response.sendRedirect("/login?error=credenciales");
        }
    }
}
