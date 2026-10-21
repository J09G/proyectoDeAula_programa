package com.proyecto.parking.controller;

import com.proyecto.parking.dto.ReservaForm;
import com.proyecto.parking.exception.ReglaNegocioException;
import com.proyecto.parking.model.Pago;
import com.proyecto.parking.model.Pago.EstadoPago;
import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Reserva.EstadoReserva;
import com.proyecto.parking.security.UsuarioPrincipal;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.EspacioService;
import com.proyecto.parking.service.PagoService;
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
    private final PagoService pagoService;

    public ReservaController(ReservaService reservaService,
                             ParqueaderoService parqueaderoService,
                             ComentarioService comentarioService,
                             EspacioService espacioService,
                             PortadaService portadaService,
                             PagoService pagoService) {
        this.reservaService = reservaService;
        this.parqueaderoService = parqueaderoService;
        this.comentarioService = comentarioService;
        this.espacioService = espacioService;
        this.portadaService = portadaService;
        this.pagoService = pagoService;
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
        // Con la pasarela activa, el formulario avisa del anticipo antes de reservar.
        model.addAttribute("anticipo", pagoService.pagosHabilitados() ? pagoService.calcularAnticipo(parqueadero) : null);
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
            Reserva reserva = reservaService.crearReserva(cliente.getId(), form);
            if (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
                // El cubículo queda apartado; falta pagar el anticipo para confirmarla.
                return "redirect:/reserva/pago/" + reserva.getId();
            }
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

    /** Una reserva ajena o inexistente sale como 404 por GlobalExceptionHandler. */
    @PostMapping("/cancelar/{idReserva}")
    public String cancelarReserva(@AuthenticationPrincipal UsuarioPrincipal cliente,
                                  @PathVariable String idReserva,
                                  RedirectAttributes flash) {
        try {
            reservaService.cancelarReserva(idReserva, cliente.getId());
            flash.addFlashAttribute("mensaje", mensajeDeCancelacion(idReserva));
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cliente?scroll=reservas";
    }

    /** Le dice al cliente qué pasó con su anticipo al cancelar. */
    private String mensajeDeCancelacion(String idReserva) {
        EstadoPago estado = pagoService.pagoDeReserva(idReserva).map(Pago::getEstado).orElse(null);
        if (estado == EstadoPago.DEVUELTO) {
            return "Reserva cancelada. Te devolvimos el anticipo.";
        }
        if (estado == EstadoPago.DEVOLUCION_PENDIENTE) {
            return "Reserva cancelada. Tu anticipo está en proceso de devolución.";
        }
        if (estado == EstadoPago.RETENIDO) {
            return "Reserva cancelada. El anticipo no se devuelve porque cancelaste muy cerca de la hora de llegada.";
        }
        return "Reserva cancelada.";
    }
}
