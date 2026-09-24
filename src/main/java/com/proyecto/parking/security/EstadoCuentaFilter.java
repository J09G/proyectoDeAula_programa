package com.proyecto.parking.security;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Revalida en cada petición que la cuenta siga activa y con el mismo rol.
 *
 * <p>Spring Security carga el usuario una sola vez, al autenticar. Sin este
 * filtro, deshabilitar a alguien o cambiarle el rol no surtía efecto hasta que
 * su sesión caducaba, y mientras tanto podía seguir operando con los permisos
 * antiguos.</p>
 */
@Component
public class EstadoCuentaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(EstadoCuentaFilter.class);

    private final UsuarioRepository usuarioRepository;

    public EstadoCuentaFilter(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return ruta.startsWith("/css/")
                || ruta.startsWith("/js/")
                || ruta.startsWith("/images/")
                || ruta.startsWith("/actuator/")
                || ruta.startsWith("/favicon.");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof UsuarioPrincipal principal) {

            Optional<Usuario> actual = usuarioRepository.findByCorreo(principal.getCorreo());

            boolean debeInvalidar = actual.isEmpty()
                    || !actual.get().isHabilitado()
                    || !new UsuarioPrincipal(actual.get()).getAuthority().equals(principal.getAuthority());

            if (debeInvalidar) {
                log.info("Sesión invalidada para {}: la cuenta cambió o fue deshabilitada.",
                        principal.getCorreo());
                cerrarSesion(request);
                response.sendRedirect(request.getContextPath() + "/login?error=deshabilitado");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void cerrarSesion(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }
}
