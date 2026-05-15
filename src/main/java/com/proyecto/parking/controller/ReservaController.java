package com.proyecto.parking.controller;

import com.proyecto.parking.model.Parqueadero;
import com.proyecto.parking.model.RegistroParqueo;
import com.proyecto.parking.model.Reserva;
import com.proyecto.parking.model.Usuario;
import com.proyecto.parking.service.ComentarioService;
import com.proyecto.parking.service.ParqueaderoService;
import com.proyecto.parking.service.RegistroParqueoService;
import com.proyecto.parking.service.ReservaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Controller
@RequestMapping("/reserva")
public class ReservaController {

    @Autowired
    private ReservaService reservaService;

    @Autowired
    private ParqueaderoService parqueaderoService;

    @Autowired
    private ComentarioService comentarioService;

    @Autowired
    private RegistroParqueoService registroParqueoService;

    @GetMapping("/{idParqueadero}")
    public String mostrarFormularioReserva(@PathVariable("idParqueadero") String idParqueadero,
                                           Model model) {
        Parqueadero parqueadero = parqueaderoService.obtenerParqueaderoPorId(idParqueadero);
        model.addAttribute("parqueadero", parqueadero);
        model.addAttribute("comentarios", comentarioService.listarPorParqueadero(idParqueadero));
        model.addAttribute("espaciosOcupados", calcularEspaciosOcupados(idParqueadero));
        return "cliente/reserva";
    }

    private Set<Integer> calcularEspaciosOcupados(String idParqueadero) {
        Set<Integer> ocupados = new HashSet<>();
        reservaService.listarReservasParqueadero(idParqueadero).stream()
                .filter(r -> r.getEstado() == Reserva.EstadoReserva.ACEPTADA)
                .filter(r -> r.getEspacioReservado() != null)
                .map(Reserva::getEspacioReservado)
                .forEach(ocupados::add);
        registroParqueoService.listarActivosPorParqueadero(idParqueadero).stream()
                .filter(r -> r.getEspacioReservado() != null)
                .map(RegistroParqueo::getEspacioReservado)
                .forEach(ocupados::add);
        return ocupados;
    }

    @PostMapping("/crear")
    public String crearReserva(@RequestParam("idParqueadero") String idParqueadero,
                               @RequestParam("fechaReserva") String fechaReservaStr,
                               @RequestParam(value = "espacioReservado", defaultValue = "0") int espacioReservado,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        try {
            Usuario cliente = (Usuario) session.getAttribute("usuario");

            if (cliente == null) {
                redirectAttributes.addFlashAttribute("error", "Debe iniciar sesión para hacer una reserva.");
                return "redirect:/login";
            }

            if (espacioReservado <= 0) {
                redirectAttributes.addFlashAttribute("error", "Debes seleccionar un espacio disponible.");
                return "redirect:/reserva/" + idParqueadero;
            }

            LocalDateTime fechaReserva = LocalDateTime.parse(fechaReservaStr);
            Reserva nuevaReserva = reservaService.crearReserva(cliente.getId(), idParqueadero, fechaReserva, espacioReservado);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Reserva creada con éxito. Estado: " + nuevaReserva.getEstado());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al crear la reserva: " + e.getMessage());
        }

        return "redirect:/cliente";
    }
}
