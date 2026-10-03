package com.proyecto.parking.config;

import com.proyecto.parking.security.UsuarioPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Antes de reservar, el cliente debe tener cédula y placa: el administrador
 * busca las reservas por cédula y reconoce el vehículo por placa. Quien entró
 * con Google llega sin ellas y aquí se le manda a completarlas.
 *
 * <p>Es un interceptor de MVC y no un filtro de seguridad porque es una regla
 * de negocio: la identidad y el rol ya los resolvieron los filtros de
 * SecurityConfig antes de llegar aquí.</p>
 */
public class PerfilCompletoInterceptor implements HandlerInterceptor {

    public static final String RUTA_COMPLETAR = "/cliente/completar-perfil";

    /**
     * Únicos destinos a los que se devuelve al cliente tras completar el perfil:
     * la página de reserva de un parqueadero, con su query opcional. Lista
     * blanca contra redirecciones abiertas: un "continuar" apuntando a otro
     * sitio se descarta.
     */
    private static final Pattern DESTINO_PERMITIDO =
            Pattern.compile("^/reserva/[A-Za-z0-9]+(\\?[A-Za-z0-9=&%:._-]*)?$");

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal
                && !principal.isPerfilCompleto()) {

            String url = request.getContextPath() + RUTA_COMPLETAR;
            // Solo se recuerda el destino de un GET (ver la página de reserva);
            // un POST a medias no se puede repetir con un redirect.
            if ("GET".equals(request.getMethod())) {
                String destino = request.getRequestURI().substring(request.getContextPath().length())
                        + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
                if (esDestinoPermitido(destino)) {
                    url += "?continuar=" + UriUtils.encodeQueryParam(destino, StandardCharsets.UTF_8);
                }
            }
            response.sendRedirect(url);
            return false;
        }
        return true;
    }

    public static boolean esDestinoPermitido(String destino) {
        return destino != null && DESTINO_PERMITIDO.matcher(destino).matches();
    }
}
