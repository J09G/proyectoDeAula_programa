package com.proyecto.parking.controller;

import com.proyecto.parking.dto.ComentarioForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.PortadaService;
import com.proyecto.parking.service.ReservaService;
import com.proyecto.parking.service.ZonaService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ClienteController {

    private final ZonaService zonaService;
    private final ReservaService reservaService;
    private final ComentarioService comentarioService;
    private final PortadaService portadaService;
    private final ParqueaderoService parqueaderoService;

    public ClienteController(ZonaService zonaService,
                             ReservaService reservaService,
                             ComentarioService comentarioService,
                             PortadaService portadaService,
                             ParqueaderoService parqueaderoService) {
        this.zonaService = zonaService;
        this.reservaService = reservaService;
        this.comentarioService = comentarioService;
        this.portadaService = portadaService;
        this.parqueaderoService = parqueaderoService;
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
