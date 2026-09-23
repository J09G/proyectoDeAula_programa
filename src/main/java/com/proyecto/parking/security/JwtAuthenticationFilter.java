package com.proyecto.parking.security;

import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.UsuarioService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioService usuarioService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validarYObtenerClaims(token);
                String correo = claims.getSubject();

                Usuario usuario = usuarioService.obtenerUsuarioPorCorreo(correo);

                if (usuario != null && usuario.isHabilitado()) {
                    String rol = claims.get("rol", String.class);
                    String authority = "ROLE_" + rol.toUpperCase();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    usuario,
                                    null,
                                    List.of(new SimpleGrantedAuthority(authority)));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
                // Usuario inexistente o deshabilitado: no se autentica, aunque el token sea válido.
            } catch (JwtException e) {
                // Token invalido o expirado: se deja sin autenticar, el endpoint protegido respondera 401.
            }
        }

        filterChain.doFilter(request, response);
    }
}
