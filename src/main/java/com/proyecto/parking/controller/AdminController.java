package com.proyecto.parking.controller;

import com.proyecto.parking.dto.EditarParqueaderoForm;
import com.proyecto.parking.dto.EntradaForm;
import com.proyecto.parking.dto.EspaciosForm;
import com.proyecto.parking.dto.ParqueaderoForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.EspacioService;
import com.proyecto.parking.service.FacturaPdfService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.RegistroParqueoService;
import com.proyecto.parking.service.ReservaService;
import com.proyecto.parking.service.ZonaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Panel del administrador de parqueaderos.
 *
 * <p><strong>Propiedad de los recursos:</strong> ninguna operación confía en el
 * id que llega por la URL. Todas pasan por
 * {@code ParqueaderoService.obtenerParqueaderoDeAdministrador(...)} o por un
 * método de servicio que recibe el id del administrador y comprueba lo mismo.
 * Antes bastaba con editar el id de la ruta para gestionar el parqueadero de
 * otro administrador: ver tarifas, aceptar reservas o descargar sus facturas.</p>
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int REGISTROS_POR_PAGINA = 10;
    private static final int RESERVAS_POR_PAGINA = 10;

    private final ParqueaderoService parqueaderoService;
    private final ReservaService reservaService;
    private final RegistroParqueoService registroParqueoService;
    private final ComentarioService comentarioService;
    private final ZonaService zonaService;
    private final EspacioService espacioService;
    private final FacturaPdfService facturaPdfService;

    public AdminController(ParqueaderoService parqueaderoService,
                           ReservaService reservaService,
                           RegistroParqueoService registroParqueoService,
                           ComentarioService comentarioService,
                           ZonaService zonaService,
                           EspacioService espacioService,
                           FacturaPdfService facturaPdfService) {
        this.parqueaderoService = parqueaderoService;
        this.reservaService = reservaService;
        this.registroParqueoService = registroParqueoService;
        this.comentarioService = comentarioService;
        this.zonaService = zonaService;
        this.espacioService = espacioService;
        this.facturaPdfService = facturaPdfService;
    }

    // ── Listado propio ───────────────────────────────────────────────────────

    @GetMapping("")
    public String listarMisParqueaderos(@AuthenticationPrincipal UsuarioPrincipal admin, Model model) {
        model.addAttribute("parqueaderos",
                parqueaderoService.obtenerParqueaderosPorAdministrador(admin.getId()));
        model.addAttribute("zonas", zonaService.obtenerZonas());
        model.addAttribute("form", new ParqueaderoForm());
        return "admin/index";
    }

    @PostMapping("/registrarParqueadero")
    public String registrarParqueadero(@AuthenticationPrincipal UsuarioPrincipal admin,
                                       @Valid @ModelAttribute("form") ParqueaderoForm form,
                                       BindingResult errores,
                                       RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return "redirect:/admin";
        }

        try {
            parqueaderoService.registrarParqueadero(form, admin.getId());
            flash.addFlashAttribute("mensaje", "Parqueadero registrado correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin";
    }

    // ── Panel de un parqueadero ──────────────────────────────────────────────

    @GetMapping("/parqueadero/{id}")
    public String verParqueadero(@AuthenticationPrincipal UsuarioPrincipal admin,
                                 @PathVariable String id,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 @RequestParam(required = false) String cedula,
                                 Model model) {
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoDeAdministrador(id, admin.getId());

        boolean buscando = cedula != null && !cedula.isBlank();
        Page<Reserva> reservas = buscarReservas(id, admin.getId(), cedula, Math.max(pagina, 0));
        // Al aceptar o eliminar la última reserva de la última página, esa
        // página queda vacía: se muestra la anterior en vez de una tabla sin filas.
        if (reservas.isEmpty() && pagina > 0 && reservas.getTotalPages() > 0) {
            reservas = buscarReservas(id, admin.getId(), cedula, reservas.getTotalPages() - 1);
        }

        if (buscando && reservas.isEmpty()) {
            model.addAttribute("error", "No se encontraron reservas para la cédula " + cedula + ".");
        }

        model.addAttribute("parqueadero", parqueadero);
        model.addAttribute("reservas", reservas.getContent());
        model.addAttribute("cedula", cedula);
        model.addAttribute("filtros", buscando
                ? "&cedula=" + URLEncoder.encode(cedula.trim(), StandardCharsets.UTF_8) : "");
        model.addAttribute("paginaActual", reservas.getNumber());
        model.addAttribute("totalPaginas", reservas.getTotalPages());
        model.addAttribute("totalElementos", reservas.getTotalElements());
        // Los indicadores cuentan todas las reservas, no sólo la página visible.
        model.addAttribute("totalPendientes",
                reservaService.contarReservasPorEstado(id, admin.getId(), EstadoReserva.PENDIENTE));
        model.addAttribute("totalAceptadas",
                reservaService.contarReservasPorEstado(id, admin.getId(), EstadoReserva.ACEPTADA));
        model.addAttribute("comentarios", comentarioService.listarPorParqueadero(id));
        return "admin/parqueadero";
    }

    private Page<Reserva> buscarReservas(String idParqueadero, String idAdministrador, String cedula, int pagina) {
        PageRequest pageable = PageRequest.of(pagina, RESERVAS_POR_PAGINA, Sort.by(Sort.Direction.DESC, "_id"));
        return cedula != null && !cedula.isBlank()
                ? reservaService.buscarReservasPorCedulaYParqueadero(cedula, idParqueadero, idAdministrador, pageable)
                : reservaService.listarReservasParqueadero(idParqueadero, idAdministrador, pageable);
    }

    /** Vuelve a la tabla de reservas en la misma página y con la misma búsqueda. */
    private static String volverAReservas(String idParqueadero, int pagina, String cedula) {
        UriComponentsBuilder destino = UriComponentsBuilder.fromPath("/admin/parqueadero/{id}")
                .queryParam("pagina", Math.max(pagina, 0));
        if (cedula != null && !cedula.isBlank()) {
            destino.queryParam("cedula", cedula.trim());
        }
        return "redirect:" + destino.fragment("reservas").buildAndExpand(idParqueadero).encode().toUriString();
    }

    // ── Configuración: datos, espacios y estado (página propia) ──────────────

    @GetMapping("/parqueadero/{id}/editar")
    public String mostrarConfiguracion(@AuthenticationPrincipal UsuarioPrincipal admin,
                                       @PathVariable String id,
                                       Model model) {
        model.addAttribute("parqueadero", parqueaderoService.obtenerParqueaderoDeAdministrador(id, admin.getId()));
        model.addAttribute("espaciosOcupados", espacioService.calcularEspaciosOcupados(id));
        model.addAttribute("espaciosPendientes", espacioService.calcularEspaciosPendientes(id));
        return "admin/editar_parqueadero";
    }

    private static String volverAConfiguracion(String idParqueadero) {
        return "redirect:/admin/parqueadero/" + idParqueadero + "/editar";
    }

    @PostMapping("/parqueadero/{id}/editar")
    public String editarParqueadero(@AuthenticationPrincipal UsuarioPrincipal admin,
                                    @PathVariable String id,
                                    @Valid @ModelAttribute EditarParqueaderoForm form,
                                    BindingResult errores,
                                    RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return volverAConfiguracion(id);
        }

        try {
            parqueaderoService.actualizarDatos(id, admin.getId(), form);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return volverAConfiguracion(id);
        }
        flash.addFlashAttribute("mensaje", "Información actualizada correctamente.");
        return volverAConfiguracion(id);
    }

    @PostMapping("/parqueadero/{id}/espacios")
    public String actualizarEspacios(@AuthenticationPrincipal UsuarioPrincipal admin,
                                     @PathVariable String id,
                                     @Valid @ModelAttribute EspaciosForm form,
                                     BindingResult errores,
                                     RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return volverAConfiguracion(id);
        }

        try {
            parqueaderoService.actualizarCapacidad(id, admin.getId(),
                    form.getEspaciosTotales(), form.getEspaciosDisponibles());
            flash.addFlashAttribute("mensaje", "Espacios actualizados correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volverAConfiguracion(id);
    }

    @PostMapping("/parqueadero/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal UsuarioPrincipal admin,
                                @PathVariable String id,
                                @RequestParam boolean habilitado,
                                RedirectAttributes flash) {
        parqueaderoService.cambiarEstado(id, admin.getId(), habilitado);
        flash.addFlashAttribute("mensaje",
                habilitado ? "Parqueadero habilitado." : "Parqueadero deshabilitado.");
        return volverAConfiguracion(id);
    }

    // ── Reservas ─────────────────────────────────────────────────────────────

    @PostMapping("/parqueadero/{id}/reserva/aceptar/{rid}")
    public String aceptarReserva(@AuthenticationPrincipal UsuarioPrincipal admin,
                                 @PathVariable String id,
                                 @PathVariable String rid,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 @RequestParam(required = false) String cedula,
                                 RedirectAttributes flash) {
        try {
            reservaService.aceptarReserva(rid, admin.getId());
            flash.addFlashAttribute("mensaje", "Reserva aceptada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volverAReservas(id, pagina, cedula);
    }

    @PostMapping("/parqueadero/{id}/reserva/rechazar/{rid}")
    public String rechazarReserva(@AuthenticationPrincipal UsuarioPrincipal admin,
                                  @PathVariable String id,
                                  @PathVariable String rid,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  @RequestParam(required = false) String cedula,
                                  RedirectAttributes flash) {
        try {
            reservaService.rechazarReserva(rid, admin.getId());
            flash.addFlashAttribute("mensaje", "Reserva rechazada.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volverAReservas(id, pagina, cedula);
    }

    @PostMapping("/parqueadero/{id}/reserva/eliminar/{rid}")
    public String eliminarReserva(@AuthenticationPrincipal UsuarioPrincipal admin,
                                  @PathVariable String id,
                                  @PathVariable String rid,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  @RequestParam(required = false) String cedula,
                                  RedirectAttributes flash) {
        reservaService.eliminarReserva(rid, admin.getId());
        flash.addFlashAttribute("mensaje", "Reserva eliminada.");
        return volverAReservas(id, pagina, cedula);
    }

    // ── Registros de parqueo ─────────────────────────────────────────────────

    @GetMapping("/parqueadero/{id}/registros")
    public String mostrarRegistros(@AuthenticationPrincipal UsuarioPrincipal admin,
                                   @PathVariable String id,
                                   @RequestParam(defaultValue = "0") int pagina,
                                   Model model) {
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoDeAdministrador(id, admin.getId());

        List<RegistroParqueo> activos =
                registroParqueoService.listarActivosPorParqueadero(id, admin.getId());

        // El historial crece sin techo: se pagina para no traer la colección
        // entera a memoria en cada visita.
        Page<RegistroParqueo> historial = registroParqueoService.listarHistorial(
                id, admin.getId(),
                PageRequest.of(Math.max(pagina, 0), REGISTROS_POR_PAGINA,
                        Sort.by(Sort.Direction.DESC, "_id")));

        model.addAttribute("parqueadero", parqueadero);
        model.addAttribute("activos", activos);
        model.addAttribute("historial", historial.getContent());
        model.addAttribute("paginaActual", historial.getNumber());
        model.addAttribute("totalPaginas", historial.getTotalPages());
        model.addAttribute("totalElementos", historial.getTotalElements());
        model.addAttribute("espaciosOcupados", espacioService.calcularEspaciosOcupados(id));
        model.addAttribute("espaciosPendientes", espacioService.calcularEspaciosPendientes(id));
        return "admin/registros";
    }

    @PostMapping("/parqueadero/{id}/registros/entrada")
    public String registrarEntrada(@AuthenticationPrincipal UsuarioPrincipal admin,
                                   @PathVariable String id,
                                   @Valid @ModelAttribute EntradaForm form,
                                   BindingResult errores,
                                   RedirectAttributes flash) {
        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return "redirect:/admin/parqueadero/" + id + "/registros";
        }

        try {
            registroParqueoService.registrarEntrada(id, admin.getId(), form);
            flash.addFlashAttribute("mensaje",
                    "Entrada registrada: placa " + form.getPlaca()
                    + ", cubículo #" + form.getEspacioReservado() + ".");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id + "/registros";
    }

    @PostMapping("/parqueadero/{id}/registros/salida/{idRegistro}")
    public String registrarSalida(@AuthenticationPrincipal UsuarioPrincipal admin,
                                  @PathVariable String id,
                                  @PathVariable String idRegistro,
                                  RedirectAttributes flash) {
        try {
            RegistroParqueo registro = registroParqueoService.registrarSalida(idRegistro, admin.getId());
            flash.addFlashAttribute("mensaje",
                    "Salida registrada. Valor a pagar: $" + registro.getValorPagado());
            flash.addFlashAttribute("idFactura", registro.getId());
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/parqueadero/" + id + "/registros";
    }

    // ── Factura ──────────────────────────────────────────────────────────────

    @GetMapping("/parqueadero/{id}/registros/factura/{idRegistro}")
    public String verFactura(@AuthenticationPrincipal UsuarioPrincipal admin,
                             @PathVariable String id,
                             @PathVariable String idRegistro,
                             Model model) {
        model.addAttribute("registro", registroParqueoService.obtenerPorId(idRegistro, admin.getId()));
        model.addAttribute("idParqueadero", id);
        return "admin/factura";
    }

    @GetMapping("/parqueadero/{id}/registros/factura/pdf/{idRegistro}")
    public void descargarFacturaPdf(@AuthenticationPrincipal UsuarioPrincipal admin,
                                    @PathVariable String id,
                                    @PathVariable String idRegistro,
                                    HttpServletResponse response) throws IOException {
        // La comprobación de propiedad ocurre antes de tocar la respuesta: si
        // falla, GlobalExceptionHandler puede devolver una página de error en vez
        // de un PDF a medio escribir.
        RegistroParqueo registro = registroParqueoService.obtenerPorId(idRegistro, admin.getId());

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=factura-" + idRegistro + ".pdf");

        facturaPdfService.generar(registro, response.getOutputStream());
    }
}
