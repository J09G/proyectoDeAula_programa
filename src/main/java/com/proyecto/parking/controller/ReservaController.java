package com.proyecto.parking.controller;

import com.proyecto.parking.dto.ReservaForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.EspacioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.PortadaService;
import com.proyecto.parking.service.ReservaService;
import jakarta.validation.Valid;
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

@Controller
@RequestMapping("/reserva")
public class ReservaController {

    private final ReservaService reservaService;
    private final ParqueaderoService parqueaderoService;
    private final ComentarioService comentarioService;
    private final EspacioService espacioService;
    private final PortadaService portadaService;

    public ReservaController(ReservaService reservaService,
                             ParqueaderoService parqueaderoService,
                             ComentarioService comentarioService,
                             EspacioService espacioService,
                             PortadaService portadaService) {
        this.reservaService = reservaService;
        this.parqueaderoService = parqueaderoService;
        this.comentarioService = comentarioService;
        this.espacioService = espacioService;
        this.portadaService = portadaService;
    }

    @GetMapping("/{idParqueadero}")
    public String mostrarFormulario(@PathVariable String idParqueadero,
                                    @RequestParam(required = false) String llegada,
                                    Model model) {
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);

        model.addAttribute("parqueadero", parqueadero);
        model.addAttribute("comentarios", comentarioService.listarPorParqueadero(idParqueadero));
        model.addAttribute("valoracion", portadaService.valoracionDe(idParqueadero));
        model.addAttribute("llegada", HomeController.esFechaDeFormulario(llegada) ? llegada : null);
        model.addAttribute("espaciosOcupados", espacioService.calcularEspaciosOcupados(idParqueadero));
        model.addAttribute("espaciosPendientes", espacioService.calcularEspaciosPendientes(idParqueadero));
        return "cliente/reserva";
    }

    @PostMapping("/crear")
    public String crearReserva(@AuthenticationPrincipal UsuarioPrincipal cliente,
                               @Valid @ModelAttribute ReservaForm form,
                               BindingResult errores,
                               RedirectAttributes flash) {

        if (errores.hasErrors()) {
            flash.addFlashAttribute("error", Errores.resumen(errores));
            return "redirect:/reserva/" + form.getIdParqueadero();
        }

        try {
            reservaService.crearReserva(cliente.getId(), form);
            flash.addFlashAttribute("mensaje",
                    "Reserva creada. Queda pendiente de que el parqueadero la confirme.");
            return "redirect:/cliente";

        } catch (ReglaNegocioException e) {
            // Se vuelve al formulario para que el cliente pueda elegir otro
            // cubículo sin perder de vista la cuadrícula.
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/reserva/" + form.getIdParqueadero();
        }
    }
}
