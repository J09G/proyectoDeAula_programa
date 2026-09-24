package com.proyecto.parking.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce las excepciones de la aplicación a las páginas de error de
 * {@code templates/error/}.
 *
 * <p>Regla de oro: al usuario sólo se le muestra el mensaje de las excepciones
 * de dominio ({@link ReglaNegocioException}, {@link RecursoNoEncontradoException}).
 * Cualquier otra excepción se registra completa en el log y al usuario se le
 * devuelve un mensaje genérico, para no filtrar detalles internos.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VISTA_403 = "error/403";
    private static final String VISTA_404 = "error/404";
    private static final String VISTA_500 = "error/500";

    @ExceptionHandler(AccesoDenegadoException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String manejarAccesoDenegado(AccesoDenegadoException ex, Model model, HttpServletRequest request) {
        log.warn("Acceso denegado en {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return VISTA_403;
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String manejarRecursoNoEncontrado(RecursoNoEncontradoException ex, Model model, HttpServletRequest request) {
        log.info("Recurso no encontrado en {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return VISTA_404;
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String manejarRutaInexistente(NoHandlerFoundException ex, Model model, HttpServletRequest request) {
        model.addAttribute("error", "La página solicitada no existe.");
        model.addAttribute("path", request.getRequestURI());
        return VISTA_404;
    }

    /**
     * Recurso estático inexistente (favicon.ico, una imagen que falta...).
     *
     * <p>Es un 404 corriente, no un fallo del servidor. Sin este manejador caía
     * en el catch-all de abajo y cada petición del navegador dejaba un stack
     * trace completo en el log, ahogando los errores que sí importan.</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String manejarRecursoEstaticoInexistente(NoResourceFoundException ex,
                                                    Model model, HttpServletRequest request) {
        log.debug("Recurso estático no encontrado: {}", request.getRequestURI());
        model.addAttribute("error", "El recurso solicitado no existe.");
        model.addAttribute("path", request.getRequestURI());
        return VISTA_404;
    }

    @ExceptionHandler(ReglaNegocioException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarReglaNegocio(ReglaNegocioException ex, Model model, HttpServletRequest request) {
        log.info("Regla de negocio incumplida en {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("error", ex.getMessage());
        model.addAttribute("path", request.getRequestURI());
        return VISTA_500;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public String manejarMetodoNoSoportado(HttpRequestMethodNotSupportedException ex,
                                           Model model, HttpServletRequest request) {
        log.info("Método {} no soportado en {}", request.getMethod(), request.getRequestURI());
        model.addAttribute("error", "Esta página no admite esa operación.");
        model.addAttribute("path", request.getRequestURI());
        return VISTA_404;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String manejarClaveDuplicada(DuplicateKeyException ex, Model model, HttpServletRequest request) {
        log.warn("Violación de índice único en {}", request.getRequestURI(), ex);
        model.addAttribute("error", "Ya existe un registro con esos datos. Verifica el correo, la cédula o la placa.");
        model.addAttribute("path", request.getRequestURI());
        return VISTA_500;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String manejarErrorInterno(Exception ex, Model model, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        model.addAttribute("error", "Ocurrió un error interno. Inténtalo de nuevo más tarde.");
        model.addAttribute("path", request.getRequestURI());
        return VISTA_500;
    }
}
