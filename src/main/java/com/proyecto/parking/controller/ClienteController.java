package com.proyecto.parking.controller;

import com.proyecto.parking.dto.ComentarioForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.FacturaPdfService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.PortadaService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class ClienteController {

    private static final int PARQUEOS_POR_PAGINA = 10;

    private final ZonaService zonaService;
    private final ReservaService reservaService;
    private final ComentarioService comentarioService;
    private final PortadaService portadaService;
    private final ParqueaderoService parqueaderoService;
    private final RegistroParqueoService registroParqueoService;
    private final FacturaPdfService facturaPdfService;

    public ClienteController(ZonaService zonaService,
                             ReservaService reservaService,
                             ComentarioService comentarioService,
                             PortadaService portadaService,
                             ParqueaderoService parqueaderoService,
                             RegistroParqueoService registroParqueoService,
                             FacturaPdfService facturaPdfService) {
        this.zonaService = zonaService;
        this.reservaService = reservaService;
        this.comentarioService = comentarioService;
        this.portadaService = portadaService;
        this.parqueaderoService = parqueaderoService;
        this.registroParqueoService = registroParqueoService;
        this.facturaPdfService = facturaPdfService;
    }

    @GetMapping("/cliente")
    public String mostrarPanel(@AuthenticationPrincipal UsuarioPrincipal cliente, Model model) {
        // El rol ya lo exige SecurityConfig; aquí no hace falta repetir la comprobación.
        model.addAttribute("zonas", portadaService.zonasConConteo());
        model.addAttribute("reservas", reservaService.listarReservasCliente(cliente.getId()));
        model.addAttribute("usuario", cliente);
        return "cliente/index";
    }

    /** Mapa con los parqueaderos ubicados (US-15). El rol lo exige SecurityConfig (/cliente/**). */
    @GetMapping("/cliente/mapa")
    public String mostrarMapa(Model model) {
        model.addAttribute("parqueaderos", parqueaderoService.listarParaMapa());
        return "cliente/mapa";
    }

    // ── Mis parqueos (US-12) ─────────────────────────────────────────────────

    @GetMapping("/cliente/parqueos")
    public String mostrarParqueos(@AuthenticationPrincipal UsuarioPrincipal cliente,
                                  @RequestParam(defaultValue = "0") int pagina,
                                  Model model) {
        // El id sale de la sesión, nunca de la URL: no hay forma de pedir el
        // historial de otro cliente.
        Page<RegistroParqueo> registros = registroParqueoService.listarDelCliente(cliente.getId(),
                PageRequest.of(Math.max(pagina, 0), PARQUEOS_POR_PAGINA, Sort.by(Sort.Direction.DESC, "_id")));

        model.addAttribute("registros", registros.getContent());
        model.addAttribute("paginaActual", registros.getNumber());
        model.addAttribute("totalPaginas", registros.getTotalPages());
        model.addAttribute("totalElementos", registros.getTotalElements());
        return "cliente/parqueos";
    }

    @GetMapping("/cliente/parqueos/{idRegistro}/factura")
    public void descargarFactura(@AuthenticationPrincipal UsuarioPrincipal cliente,
                                 @PathVariable String idRegistro,
                                 HttpServletResponse response) throws IOException {
        // Se comprueba antes de tocar la respuesta: si el registro es ajeno o
        // sigue activo, GlobalExceptionHandler devuelve el 404 en vez de un PDF
        // a medio escribir.
        RegistroParqueo registro = registroParqueoService.obtenerFinalizadoDelCliente(idRegistro, cliente.getId());

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=factura-" + idRegistro + ".pdf");
        facturaPdfService.generar(registro, response.getOutputStream());
    }

    @PostMapping("/comentario/crear")
    public String crearComentario(@AuthenticationPrincipal UsuarioPrincipal cliente,
                                  @Valid @ModelAttribute ComentarioForm form,
                                  BindingResult errores,
                                  RedirectAttributes flash) {

        String destino = "redirect:/reserva/" + form.getIdParqueadero();

        if (errores.hasErrors()) {
            flash.addFlashAttribute("errorComentario", Errores.resumen(errores));
            return destino;
        }

        try {
            comentarioService.crearComentario(cliente.getId(), form.getIdParqueadero(),
                    form.getTexto(), form.getPuntuacion());
            flash.addFlashAttribute("mensajeComentario", "Comentario publicado correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("errorComentario", e.getMessage());
        }

        return destino;
    }

}
