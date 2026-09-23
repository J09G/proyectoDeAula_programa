package com.proyecto.parking.security;

import com.proyecto.parking.model.Rol;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = new JwtService("dev-only-secret-cambiar-en-produccion-32chars", 900000);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthenticationFilter construirFiltro(UsuarioService usuarioService) {
        JwtAuthenticationFilter filtro = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filtro, "jwtService", jwtService);
        ReflectionTestUtils.setField(filtro, "usuarioService", usuarioService);
        return filtro;
    }

    @Test
    void noAutenticaSiElUsuarioEstaDeshabilitadoAunConTokenValido() throws Exception {
        String token = jwtService.generarToken("admin@parking.com", "Administrador");

        Usuario usuarioDeshabilitado = new Usuario();
        usuarioDeshabilitado.setCorreo("admin@parking.com");
        usuarioDeshabilitado.setHabilitado(false);
        usuarioDeshabilitado.setRol(new Rol("Administrador"));

        UsuarioService usuarioService = Mockito.mock(UsuarioService.class);
        Mockito.when(usuarioService.obtenerUsuarioPorCorreo("admin@parking.com")).thenReturn(usuarioDeshabilitado);

        JwtAuthenticationFilter filtro = construirFiltro(usuarioService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/zonas");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter((HttpServletRequest) request, (HttpServletResponse) response);
    }

    @Test
    void autenticaSiElUsuarioEstaHabilitadoYElTokenEsValido() throws Exception {
        String token = jwtService.generarToken("admin@parking.com", "Administrador");

        Usuario usuarioHabilitado = new Usuario();
        usuarioHabilitado.setCorreo("admin@parking.com");
        usuarioHabilitado.setHabilitado(true);
        usuarioHabilitado.setRol(new Rol("Administrador"));

        UsuarioService usuarioService = Mockito.mock(UsuarioService.class);
        Mockito.when(usuarioService.obtenerUsuarioPorCorreo("admin@parking.com")).thenReturn(usuarioHabilitado);

        JwtAuthenticationFilter filtro = construirFiltro(usuarioService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/zonas");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filtro.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
