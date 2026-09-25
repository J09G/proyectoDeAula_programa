package com.proyecto.parking.security;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Autentica la app web (Thymeleaf) leyendo el JWT desde una cookie, en vez de
 * sesion de servidor. Es el equivalente de {@link JwtAuthenticationFilter}
 * (que solo lee la cabecera Authorization para la API) pero para el navegador.
 *
 * <p>Solo lee la cookie, nunca la cabecera Authorization: si aceptara ambas
 * fuentes en la misma cadena, un JWT pensado para un cliente de la API
 * quedaria tambien valido aqui, mezclando dos superficies que deben quedar
 * separadas (la API no lleva CSRF; la app web si, porque su autenticacion
 * ahora viaja en una cookie que el navegador manda solo).</p>
 */
@Component
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtCookieAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Optional<String> token = obtenerTokenDeCookie(request);

        if (token.isPresent()) {
            try {
                Claims claims = jwtService.validarYObtenerClaims(token.get());
                String correo = claims.getSubject();

                Usuario usuario = usuarioRepository.findByCorreo(correo).orElse(null);

                if (usuario != null && usuario.isHabilitado() && usuario.getRol() != null) {
                    UsuarioPrincipal principal = new UsuarioPrincipal(usuario);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // Usuario inexistente, deshabilitado o sin rol: no se autentica, aunque el token sea valido.
            } catch (JwtException e) {
                // Token invalido o expirado: se deja sin autenticar; las rutas protegidas
                // redirigiran al login por el authenticationEntryPoint de la cadena web.
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> obtenerTokenDeCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (JwtService.NOMBRE_COOKIE.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
